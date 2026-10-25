package tv.dumbtv.home;

/** Veto's Live TV channels as recorded on this TV (work/record_veto.py): name, logo, stream. */
final class VetoLive {
    static final Channels.Channel[] ALL = {
        new Channels.Channel("NDTV India", R.drawable.ch_ndtv_india, "https://ndtv-veto.akamaized.net/vglive-sk-834057/main.m3u8", null),
        new Channels.Channel("India TV", R.drawable.ch_india_tv, "https://veto.akamaized.net/v1/vglive-sk-274906/main.m3u8", null),
        new Channels.Channel("India TV Speed News", R.drawable.ch_speed_news, "https://veto.akamaized.net/v1/vglive-sk-479089/main.m3u8", null),
    };

    private VetoLive() {}
}
