package cc.nimbus.ui.clickgui;

public final class ClickGuiIcons {
    public static final String SEARCH = svg("<circle cx='11' cy='11' r='7'/><path d='m20 20-4-4'/>");
    public static final String CONFIG = svg("<path d='M4 6h16M4 12h16M4 18h16'/><circle cx='8' cy='6' r='2'/><circle cx='16' cy='12' r='2'/><circle cx='10' cy='18' r='2'/>");
    public static final String MUSIC = svg("<path d='M9 18V5l10-2v13'/><circle cx='6' cy='18' r='3'/><circle cx='16' cy='16' r='3'/>");
    public static final String SETTINGS = svg("<circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z'/>");
    public static final String CLOSE = svg("<path d='M6 6l12 12M18 6 6 18'/>");
    public static final String BACK = svg("<path d='m15 18-6-6 6-6'/>");
    public static final String PLAY = svg("<path d='m8 5 11 7-11 7Z'/>");
    public static final String PAUSE = svg("<path d='M8 5v14M16 5v14'/>");
    public static final String PREVIOUS = svg("<path d='M6 5v14M18 6l-9 6 9 6Z'/>");
    public static final String NEXT = svg("<path d='M18 5v14M6 6l9 6-9 6Z'/>");
    public static final String HOME = svg("<path d='m3 11 9-8 9 8'/><path d='M5 10v10h14V10'/>");
    public static final String SPARKLES = svg("<path d='m12 3 1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5Z'/><path d='m19 15 .8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8Z'/>");
    public static final String HEART = svg("<path d='M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z'/>");
    public static final String DOWNLOAD = svg("<path d='M12 3v12m0 0 4-4m-4 4-4-4'/><path d='M5 20h14'/>");
    public static final String CACHE = svg("<rect x='3' y='5' width='18' height='14' rx='2'/><path d='M7 9h10M7 13h6'/>");
    public static final String QUEUE = svg("<path d='M4 6h12M4 12h12M4 18h8'/><path d='m17 16 4 2-4 2Z'/>");
    public static final String REPEAT = svg("<path d='m17 2 4 4-4 4'/><path d='M3 11V9a3 3 0 0 1 3-3h15'/><path d='m7 22-4-4 4-4'/><path d='M21 13v2a3 3 0 0 1-3 3H3'/>");
    public static final String SHUFFLE = svg("<path d='M3 7h3c4 0 6 10 10 10h5'/><path d='m18 14 3 3-3 3'/><path d='M3 17h3c1.5 0 2.8-1.4 4-3'/><path d='M14 8c.7-.6 1.3-1 2-1h5'/><path d='m18 4 3 3-3 3'/>");
    public static final String VOLUME = svg("<path d='M11 5 6 9H3v6h3l5 4Z'/><path d='M15 9a4 4 0 0 1 0 6M18 6a8 8 0 0 1 0 12'/>");
    public static final String TRASH = svg("<path d='M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14'/>");
    public static final String LOGOUT = svg("<path d='M10 17l5-5-5-5M15 12H3'/><path d='M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5'/>");
    public static final String USER = svg("<circle cx='12' cy='8' r='4'/><path d='M4 21a8 8 0 0 1 16 0'/>");
    public static final String FOLDER = svg("<path d='M3 6h7l2 2h9v11H3Z'/>");

    private ClickGuiIcons() {
    }

    private static String svg(String body) {
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" + body + "</svg>";
    }
}
