package net.alcaris.plugin.economy.config;

public final class MessageConfig {

    private MessageConfig() {}

    public static final String PREFIX = "&8[&6Economy&8] &r";

    public static final String NO_PERMISSION        = PREFIX + "&cこのコマンドを使用する権限がありません。";
    public static final String PLAYER_ONLY          = PREFIX + "&cこのコマンドはプレイヤーのみ使用できます。";
    public static final String PLAYER_NOT_FOUND     = PREFIX + "&cプレイヤーが見つかりません: &e{player}";
    public static final String INVALID_AMOUNT       = PREFIX + "&c無効な金額です。正の数値を入力してください。";
    public static final String USAGE                = PREFIX + "&c使い方: &e{usage}";

    public static final String ACCOUNT_CREATED           = PREFIX + "&e{player} &aのアカウントを作成しました。";
    public static final String ACCOUNT_REMOVED           = PREFIX + "&e{player} &aのアカウントを削除しました。";
    public static final String NO_ACCOUNT                = PREFIX + "&c経済アカウントを持っていません。";
    public static final String TARGET_NO_ACCOUNT         = PREFIX + "&e{player} &cは経済アカウントを持っていません。";
    public static final String ACCOUNT_EXISTS            = PREFIX + "&e{player} &cはすでにアカウントを持っています。";
    public static final String ACCOUNT_ALREADY_EXISTS_SELF = PREFIX + "&cすでにアカウントを持っています。";

    public static final String BALANCE_SELF        = PREFIX + "&a残高: &e{balance}";
    public static final String BALANCE_OTHER       = PREFIX + "&e{player}&a の残高: &e{balance}";
    public static final String BALANCE_SET         = PREFIX + "&e{player}&a の残高を &e{amount}&a に設定しました。";
    public static final String BALANCE_GIVEN       = PREFIX + "&e{player}&a に &e{amount}&a を付与しました。";
    public static final String BALANCE_TAKEN       = PREFIX + "&e{player}&a から &e{amount}&a を徴収しました。";
    public static final String INSUFFICIENT_FUNDS  = PREFIX + "&c残高が不足しています。必要額: &e{amount}&c、現在残高: &e{balance}&c。";

    public static final String PAY_SENT      = PREFIX + "&e{player}&a に &e{amount}&a を送金しました。手数料: &e{fee}&a。";
    public static final String PAY_RECEIVED  = PREFIX + "&e{player}&a から &e{amount}&a を受け取りました。";
    public static final String PAY_SELF      = PREFIX + "&c自分自身には送金できません。";
    public static final String PAY_LIMIT     = PREFIX + "&c送金額が上限を超えています。";
    public static final String SENDER_FROZEN   = PREFIX + "&cアカウントが凍結されています。&e/bank unfreeze &cで解除できます。";
    public static final String RECEIVER_FROZEN = PREFIX + "&e{player}&a のアカウントは凍結中のため受け取れません。";

    public static final String ACCOUNT_FROZEN       = PREFIX + "&cアクティビティ不足のため、アカウントが凍結されました。";
    public static final String FREEZE_WARNING       = PREFIX + "&e警告: アクティビティ不足のため、&c{days}日後&e にアカウントが凍結されます。";
    public static final String FREEZE_ADMIN         = PREFIX + "&e{player}&a のアカウントを凍結しました。";
    public static final String UNFREEZE_ADMIN       = PREFIX + "&e{player}&a のアカウントの凍結を解除しました。";
    public static final String UNFREEZE_SELF        = PREFIX + "&aアカウントの凍結を解除しました。手数料: &e{fee}&a。";
    public static final String UNFREEZE_NOT_FROZEN  = PREFIX + "&cアカウントは凍結されていません。";
    public static final String UNFREEZE_INSUFFICIENT = PREFIX + "&c凍結解除手数料 &e{fee}&c を支払うための現金が不足しています。";

    public static final String CASH_DEPOSITED  = PREFIX + "&e{amount}&a を口座に入金しました。";
    public static final String CASH_WITHDRAWN  = PREFIX + "&e{amount}&a を口座から出金しました。";
    public static final String CASH_NO_ITEMS   = PREFIX + "&cインベントリに有効な現金アイテムがありません。";
    public static final String INVENTORY_FULL  = PREFIX + "&cインベントリに空きがありません。";
    public static final String CASH_FORGED     = PREFIX + "&c偽造紙幣が検出され、没収されました。";

    public static final String CRYPTO_BOUGHT       = PREFIX + "&e{symbol}&a を &e{amount}&a 購入しました。費用: &e{cost}&a。手数料: &e{fee}&a。";
    public static final String CRYPTO_SOLD         = PREFIX + "&e{symbol}&a を &e{amount}&a 売却しました。受取額: &e{received}&a。手数料: &e{fee}&a。";
    public static final String CRYPTO_INSUFFICIENT  = PREFIX + "&e{symbol}&a の保有量が不足しています。";
    public static final String CRYPTO_DISABLED      = PREFIX + "&c仮想通貨システムは現在無効です。";
    public static final String CRYPTO_NOT_FOUND     = PREFIX + "&c不明な仮想通貨: &e{symbol}&c。";
    public static final String CRYPTO_RATE_SET      = PREFIX + "&e{symbol}&a のレートを &e{rate}&a に設定しました。";

    public static final String TREASURY_NOT_FOUND    = PREFIX + "&c国庫が見つかりません: &e{key}&c。";
    public static final String TREASURY_DEPOSITED    = PREFIX + "&e{amount}&a を国庫 &e{key}&a に入金しました。";
    public static final String TREASURY_WITHDRAWN    = PREFIX + "&e{amount}&a を国庫 &e{key}&a から &e{player}&a に出金しました。";
    public static final String TREASURY_TRANSFERRED  = PREFIX + "&e{from}&a から &e{to}&a へ &e{amount}&a を移動しました。";
    public static final String TREASURY_INSUFFICIENT = PREFIX + "&c国庫の残高が不足しています。";

    public static final String INTEREST_RECEIVED = PREFIX + "&a利子が付きました: &e{amount}&a（活動スコア: &e{score}&a）";

    public static final String RELOADED = PREFIX + "&aプラグイン設定をリロードしました。";

    public static final String TOP_HEADER = "&8&m----&r &6&l残高ランキング &8(ページ {page}/{max}) &8&m----";
    public static final String TOP_ENTRY  = "&e{rank}. &f{player}&8: &a{balance}";
    public static final String TOP_EMPTY  = PREFIX + "&cアカウントが見つかりません。";

    public static final String PAY_FEE_TOO_HIGH = PREFIX + "&c手数料（&e{fee}&c）が送金額（&e{amount}&c）以上のため送金できません。送金額を増やしてください。";

    public static final String ACCOUNT_INFO_HEADER   = "&8&m----&r &6アカウント情報: &e{player} &8&m----";
    public static final String ACCOUNT_INFO_BALANCE  = " &7残高: &a{balance}";
    public static final String ACCOUNT_INFO_FROZEN   = " &7状態: {frozen}";
    public static final String ACCOUNT_INFO_LAST_TXN = " &7最終取引: &f{time}";

    public static String format(String message, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }
}
