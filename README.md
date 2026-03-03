# Obfeco

**Obfeco** is a powerful, modern, multi-currency economy solution for Minecraft servers. Create unlimited currencies, manage them through an intuitive GUI, and enjoy seamless integration with your favorite plugins.

## ✨ Key Features

*   **Unlimited Currencies:** Create as many currencies as you need (Gems, Coins, Souls, etc.).
*   **Intuitive GUI:** Manage all your currencies and view leaderboards through an easy-to-use interface.
*   **Robust Storage:** Choose between **YAML**, **SQLite**, or **MySQL** for high-performance data handling.
*   **PlaceholderAPI Support:** Display balances, total economy values, and leaderboards anywhere.
*   **Vault Integration:** Fully compatible with plugins that use Vault (requires a primary currency to be set).
*   **Admin Tools:** Comprehensive tools for giving, taking, setting, and resetting balances.
*   **Easy Migration:** Built-in tools to migrate from other economy plugins like CoinsEngine.

## 🚀 Getting Started

1.  Download the latest `Obfeco.jar`.
2.  Drop the jar file into your server's `plugins` folder.
3.  (Highly Recommended) Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/).
4.  Restart your server.
5.  Configure your primary currency in `config.yml` (especially for Vault support).

## 🛠 Commands & Permissions

The main command is `/obfeco`, with aliases `/eco` and `/economy`.

### User Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/obfeco balance [currency]` | Check your own balance | `obfeco.balance` |
| `/obfeco balance <player> <currency>` | Check another player's balance | `obfeco.balance.others` |
| `/obfeco pay <player> <currency> <amount>` | Pay another player | `obfeco.pay` |
| `/obfeco top <currency> [page]` | View the leaderboard (GUI/Chat) | `obfeco.top` / `obfeco.top.gui` |

### Admin Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/obfeco manage` | Open the Currency Manager GUI | `obfeco.gui` |
| `/obfeco create <id> <name> [start] [decimals]` | Create a new currency | `obfeco.create` |
| `/obfeco delete <currency>` | Permanently delete a currency | `obfeco.delete` |
| `/obfeco give <player> <currency> <amount> [-s]` | Give currency to a player | `obfeco.give` |
| `/obfeco take <player> <currency> <amount> [-s]` | Take currency from a player | `obfeco.take` |
| `/obfeco set <player> <currency> <amount> [-s]` | Set a player's balance | `obfeco.set` |
| `/obfeco reset <currency> [confirm]` | Reset ALL player data for a currency | `obfeco.reset` |
| `/obfeco scan coinsengine` | Scan for currencies to migrate | `obfeco.admin` |
| `/obfeco convert coinsengine` | Migrate data from CoinsEngine | `obfeco.convert` |
| `/obfeco reload` | Reload the plugin configuration | `obfeco.reload` |

*Note: Use `-s` in admin commands to perform the action silently (no notification to the target).*

## 📊 Placeholders

Obfeco provides a simple and powerful placeholder system. Replace `<currency>` with your currency ID (e.g., `coins`).

### 👤 Player Balances
| Placeholder | Description |
| :--- | :--- |
| `%obfeco_<currency>_balance%` | Player's formatted balance (e.g., 1,500.50) |
| `%obfeco_<currency>_raw%` | Player's raw numeric balance (e.g., 1500.5) |

*Tip: You can also use `%obfeco_balance_<currency>%` if you prefer.*

### 🏆 Leaderboards (Top Balances)
| Placeholder | Description |
| :--- | :--- |
| `%obfeco_<currency>_top_name_<pos>%` | Name of the player at position (1, 2, 3...) |
| `%obfeco_<currency>_top_balance_<pos>%` | Formatted balance at position |
| `%obfeco_top_<pos>_<currency>%` | Quickest way to get formatted balance |

**Examples:**
- `%obfeco_coins_top_name_1%` → Name of the richest player.
- `%obfeco_top_1_coins%` → Formatted balance of the richest player.
- `%obfeco_coins_top_raw_1%` → Raw balance of the richest player.

### 🌍 Global Currency Info
| Placeholder | Description |
| :--- | :--- |
| `%obfeco_<currency>_symbol%` | The currency's symbol (e.g., $) |
| `%obfeco_<currency>_name%` | The currency's display name |
| `%obfeco_<currency>_total_balance%` | Total amount of this currency in the economy |

---

*Note: The system is very flexible and supports many aliases (like `amount` for `balance`) and different orders, but the ones above are recommended for consistency.*

## 📦 Storage Options

Obfeco supports multiple storage types, configurable in `config.yml`:

*   **SQLite (Default):** Efficient local database, recommended for most servers.
*   **MySQL:** High-performance remote database, ideal for networks and large servers.

## 🔄 Migration

Switching from **CoinsEngine**? It's easy:
1.  Run `/obfeco scan coinsengine` to see what can be migrated.
2.  Run `/obfeco convert coinsengine` to migrate all currencies and player balances.

## 🆘 Support

Need help? Join our community:
*   **Discord:** [BusyBee Support](https://discord.gg/abdm29q7af)
