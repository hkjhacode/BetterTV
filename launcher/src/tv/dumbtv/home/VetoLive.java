package tv.dumbtv.home;

/**
 * Veto's live channels on the home row: popular Hindi and English national news only, no regional,
 * music, movie or devotional channels. Streams recorded on this TV with work/record_veto.py.
 */
final class VetoLive {
    static final Channels.Channel[] ALL = {
        new Channels.Channel("NDTV India", R.drawable.ch_ndtv_india, "https://ndtv-veto.akamaized.net/vglive-sk-834057/main.m3u8", null),
        new Channels.Channel("NDTV 24x7", R.drawable.ch_ndtv_24x7, "https://ndtv-veto.akamaized.net/vglive-sk-722277/main.m3u8", null),
        new Channels.Channel("India TV", R.drawable.ch_india_tv, "https://veto.akamaized.net/v1/vglive-sk-274906/main.m3u8", null),
        new Channels.Channel("India TV Speed News", R.drawable.ch_speed_news, "https://veto.akamaized.net/v1/vglive-sk-479089/main.m3u8", null),
        new Channels.Channel("News Nation", R.drawable.ch_news_nation, "https://nn-veto.akamaized.net/national/master_nnna_veto.m3u8", null),
        new Channels.Channel("NDTV Profit", R.drawable.ch_ndtv_profit, "https://ndtvprofit-veto.akamaized.net/ndtvprofit/master_1.m3u8", null),
    };

    private VetoLive() {}
}
