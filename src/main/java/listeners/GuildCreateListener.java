package listeners;

import commands.classic.HelpCommand;
import commands.config.*;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.discordjson.json.MessageData;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Permission;
import enums.Language;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import util.ClientConfig;
import data.Constants;
import data.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Reporter;
import util.Translator;

/**
 * Created by steve on 14/07/2016.
 */
public class GuildCreateListener {

    private final static Logger LOG = LoggerFactory.getLogger(GuildCreateListener.class);

    public Flux<MessageData> onReady(GuildCreateEvent event) {
        try {
            if (!Guild.getGuilds().containsKey(event.getGuild().getId().asString())) {

                return event.getGuild().getChannels()
                        .filter(chan -> chan instanceof GuildMessageChannel)
                        .map(chan -> (GuildMessageChannel) chan).take(1).flatMap(chan -> {
                    Guild guild = new Guild(event.getGuild().getId().asString(), event.getGuild().getName(),
                            Translator.detectLanguage(chan));
                    guild.addToDatabase();

                    Language lg = guild.getLanguage();
                    LOG.info("La guilde " + guild.getId() + " - " + guild.getName() + " a ajouté " + Constants.name);

                    return event.getGuild().getOwner().flatMap(owner -> {
                        String customMessage = Translator.getLabel(lg, "welcome.message")
                                .replaceAll("\\{name}", Constants.name)
                                .replaceAll("\\{game}", Constants.game.getName())
                                .replaceAll("\\{prefix}", Constants.prefixCommand)
                                .replaceAll("\\{help}", HelpCommand.NAME)
                                .replaceAll("\\{server}", new ServerCommand().getName())
                                .replaceAll("\\{lang}", new LanguageCommand().getName())
                                .replaceAll("\\{twitter}", new TwitterCommand().getName())
                                .replaceAll("\\{almanax-auto}", new AlmanaxAutoCommand().getName())
                                .replaceAll("\\{rss}", new RSSCommand().getName())
                                .replaceAll("\\{owner}", owner.getMention())
                                .replaceAll("\\{guild}", event.getGuild().getName());

                        return chan.createMessage(customMessage)
                                .onErrorResume(ignored -> sendWelcomeMessageInPM(event, customMessage))
                                .then(ClientConfig.DISCORD().getChannelById(Snowflake.of(Constants.chanReportID))
                                        .createMessage("[NEW] **" + event.getGuild().getName()
                                                + "** (" + guild.getLanguage().getAbrev() + "), +"
                                                + event.getGuild().getMemberCount() +  " utilisateurs"));
                    });
                });
            }
        } catch(Exception e){
            Reporter.report(e, event.getGuild());
            LOG.error("onReady", e);
        }
        return Flux.empty();
    }

    public Mono<Message> sendWelcomeMessageInPM(GuildCreateEvent event, String message){
        return event.getGuild().getOwner()
                .flatMap(Member::getPrivateChannel)
                .flatMap(ownerChan -> ownerChan.createMessage(message)
                        .onErrorResume(ClientException.isStatusCode(403), err -> Mono.empty()));
    }
}
