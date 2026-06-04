# Xposed-Mpesa-parser

LSPosed module that reads M-PESA SMS messages and POSTs parsed transaction data to an n8n webhook.

For outgoing payments, a silent notification appears with four category buttons, the chosen category is POSTed to a second webhook. Failed post requests are queued and sent once connectivity returns.

On the server, n8n stores transactions in PostgreSQL and runs scheduled jobs that aggregate spending by category, analyze usage patterns and send daily and monthly summaries to Telegram.
