package hjeisa.waifubot;

import hjeisa.waifubot.model.Request;
import hjeisa.waifubot.posting.PostController;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerMessageListener extends ListenerAdapter {

    // schedules post cycles
    private PostController postController = new PostController();
    // holds all requests not cancelled
    private List<Request> requestList = new ArrayList<>();
    // holds each user's best girl
    private Map<User, String> bestGirlMap = new HashMap<>();



    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        // anti bot security
        // TODO: create common class for listener implementations?
        if(event.getAuthor().isBot()) return;

        if(Config.debug){
            System.out.println("#" + event.getChannel().getName() + ": <"
                            + event.getAuthor().getName() + "> "
                            + event.getMessage().getContent());
        }

        try {
            ping(event);
            post(event);
            cancel(event);
            list(event);
            bestgirl(event);
        }
        catch(Exception e) {
            event.getChannel().sendMessage("An unexpected error occurred while processing your request.").queue();
        }
    }

    ////////////////////////////////////////////////////////// functions below

    private void ping(GuildMessageReceivedEvent event){
        Message message = event.getMessage();
        String content = message.getRawContent();
        MessageChannel chan = message.getChannel();

        // ping function
        if(content.equalsIgnoreCase("!ping")) {
            chan.sendMessage("Pong!").queue();
        }
    }

    private void post(GuildMessageReceivedEvent event){
        Message message = event.getMessage();
        String content = message.getRawContent();
        MessageChannel chan = message.getChannel();

        // create request
        if(content.toLowerCase().startsWith("!post ")) {
            if(content.split(" ").length > 2){
                int indexOfSearchTags = content.indexOf(' ',6);
                String intervalString = content.substring(6, indexOfSearchTags);
                String searchTags = content.substring(indexOfSearchTags + 1);

                try {
                    // Duration.parse requires "pt" prefix
                    long intervalSeconds = Duration.parse("pt" + intervalString).getSeconds();
                    if(intervalSeconds < 0)
                        throw new DateTimeParseException("Time can't be negative", intervalString, 0);

                    if(!Config.debug){
                        if (intervalSeconds < Config.min_posting_interval) {
                            throw new DateTimeParseException("Time is too short. Minimum interval is " +
                                    Util.parseDuration(Config.min_posting_interval) + "", intervalString, 0);
                        }
                    }
                    else if(intervalSeconds == 0){
                        throw new DateTimeParseException("Time can't be zero.", intervalString, 0);
                    }

                    if(Util.findRequestBySearchText(requestList, chan, searchTags) == null){
                        Request request = new Request(event.getGuild(), event.getChannel(), intervalSeconds, searchTags);
                        requestList.add(request);
                        postController.schedulePostCycle(request);

                        chan.sendMessage("Request added. Posting pictures matching \"" + searchTags + "\" tags every " +
                                Util.parseDuration(intervalSeconds) + ".").queue();
                    }
                    else {
                        chan.sendMessage("I'm already posting pictures with the same tags in this channel.").queue();
                    }
                }
                catch (DateTimeParseException e) {
                    chan.sendMessage(e.getMessage() + ".").queue();
                }
            }
            else {
                chan.sendMessage("Invalid number of arguments. Correct form is:\n"+
                        "`!post <interval> <search string>`").queue();
            }
        }
    }

    private void cancel(GuildMessageReceivedEvent event){
        Message message = event.getMessage();
        String content = message.getRawContent();
        MessageChannel chan = message.getChannel();

        // cancel post cycle
        if(content.toLowerCase().startsWith("!cancel")){
            // if command has tag parameters
            if(content.split(" ").length > 1){
                String searchTags = content.substring(8);
                Request request = Util.findRequestBySearchText(requestList, chan, searchTags);

                if(request != null){
                    if(postController.cancelPostCycle(request)){
                        chan.sendMessage("Cancelled request for tags \"" + request.getSearchTags() + "\".").queue();
                    }
                    else {
                        chan.sendMessage("Could not cancel request.").queue();
                    }
                }
                else {
                    chan.sendMessage("No request matching criteria found.").queue();
                }
            }
        }
        else if(content.toLowerCase().equals("!cancel")){
            int cancelled = postController.cancelChannelPostCycles(chan);

            if (cancelled == 0){
                chan.sendMessage("No requests to cancel for this channel.").queue();
            }
            else {
                chan.sendMessage("Cancelled all requests for this channel.").queue();
            }
        }
    }

    private void list(GuildMessageReceivedEvent event){
        Message message = event.getMessage();
        String content = message.getRawContent();
        MessageChannel chan = message.getChannel();

        // list all current posting cycles
        if(content.toLowerCase().startsWith("!list")){
            List<Request> requestsForChannel = Util.findAllRequestsByChannel(requestList, chan);

            if(requestsForChannel.size() > 0){
                StringBuilder str = new StringBuilder("Image posting cycles for this channel: ");
                for(Request req : requestsForChannel){
                    str.append("\n- Tags: \"")
                       .append(req.getSearchTags())
                       .append("\" every ")
                       .append(Util.parseDuration(req.getTimeInterval()));
                }
                chan.sendMessage(str.toString()).queue();
            }
            else {
                chan.sendMessage("I'm not posting any images in this channel.").queue();
            }
        }
    }

    private void bestgirl(GuildMessageReceivedEvent event) {
        // TODO: store user's chosen best girl to file, reload on reboot
        Message message = event.getMessage();
        String content = message.getRawContent();
        MessageChannel chan = message.getChannel();

        // posts user's best girl if one is found
        if(content.toLowerCase().startsWith("!bestgirl")){
            String girlToPost;
            // sets user's best girl
            if(content.toLowerCase().startsWith("!bestgirl set ") && content.split(" ").length >= 3){
                girlToPost = content.substring("!bestgirl set ".length());
                bestGirlMap.put(message.getAuthor(), girlToPost);
                chan.sendMessage("Ok, recognized your best girl.").queue();
            }
            else {
                girlToPost = bestGirlMap.get(message.getAuthor());
                if(girlToPost == null){
                    chan.sendMessage("Set your best girl with the `!bestgirl set <character>` command first.").queue();
                    return;
                }
                chan.sendMessage(girlToPost + "!").queue();
            }
            Request request = new Request(event.getGuild(), event.getChannel(), 0, girlToPost + " 1girl");
            postController.schedulePostOnce(request);
        }
    }
}
