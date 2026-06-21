package golden.botc_mc.botc_mc.game.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import golden.botc_mc.botc_mc.botc;

/**
 * Fabric entrypoint registered under the "voicechat" key in fabric.mod.json.
 * Simple Voice Chat calls {@link #initialize} when the voice chat server starts.
 * When Simple Voice Chat is not installed this class is never loaded.
 */
public class BotcVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return botc.ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            VoiceService.initialize(serverApi);
            botc.LOGGER.info("[Voice] Simple Voice Chat integration enabled.");
        }
    }
}
