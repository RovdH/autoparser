# autoparser

Maak `src/main/resources/application.yml` aan:

```yaml
server:
  # De beheerinterface en wijzigende endpoint blijven alleen lokaal bereikbaar.
  address: 127.0.0.1

woocommerce:
  base-url: "https://voorbeeld.nl/wp-json/wc/v3"
  consumer-key: "CONSUMERKEY"
  consumer-secret: "CONSUMERSECRET"
  # Exacte order-meta key van de bezorgdatum uit de actieve bezorgplugin.
  delivery-date-meta-key: "_wkdo_delivery_date"
```

Open na het starten `http://localhost:8080/` voor de lokale beheerinterface.
De WooCommerce REST API-key moet **Lezen/schrijven** toestaan om orders gereed te melden.

## Word-uitdraai

- `GET /orders/docx` — standaard de bezorgingen van morgen.
- `GET /orders/docx?date=2026-07-15` — één bezorgdatum.
- `GET /orders/docx?from=2026-07-15&to=2026-07-18` — inclusieve datumrange.

De persoonlijke boodschap wordt gelezen uit orderregelmeta `Persoonlijke boodschap`, zoals
opgeslagen door de WooCommerce-hook `woocommerce_checkout_create_order_line_item`.

## Orders gereedmelden

- `GET /orders/summary?date=2026-07-15` — preview met aantal en ordernummers.
- `POST /orders/complete?date=2026-07-15` — zet de gevonden processing-orders op completed.
- Zonder `date` gebruiken beide endpoints standaard de bezorgdatum van morgen.
