# manufacturing-demo — OFBiz as a Manufacturing + MRP system (demo / reference environment)

A loadable, **self-contained** Apache OFBiz component that stands up a **skateboard
factory** on stock entities, so you can explore OFBiz configured as a Manufacturing + MRP
system and reproduce the published manufacturing how-to series end to end. It brings its
own company, login and all manufacturing data — nothing is silently pulled from the stock
`demo` data set.

The scenario and sample data are the work of **Swapnil Shah** (see the Apache OFBiz
cwiki, *Manufacturing* space). Product, routing and machine IDs are preserved verbatim
so the demo lines up with those articles.

## What this first slice models

The **standing manufacturing configuration** — the "at rest" picture of a manufacturing
company's OFBiz instance. No transactions yet (no production runs, inventory, orders,
forecasts or production costing results).

- **Company & plant** — its own `Company` organization (copied from OFBiz's `AccountingDemoData`,
  tagged `MANUFACTURER`) and a production plant facility `MFG_PLANT` (type `PLANT`).
- **Login** — a focused, manufacturing-capable `admin` user (password `ofbiz`) in a
  `FULLADMIN` group granted only this demo's permissions (`OFBTOOLS_VIEW`, `CATALOG_VIEW`,
  `MANUFACTURING_*`, plus `WORKEFFORTMGR_ADMIN` / `ASSETMAINT_ADMIN` — production runs are
  WorkEfforts). It drives the Manufacturing app, not unrelated back-office apps.
- **Products** — the finished skateboard deck (`DCGAPSOSR12`), its sub-assembly
  (`DSK15144-00`), and the purchased raw materials (glue, maple veneers, sticker,
  warranty card, transfer), correctly typed `FINISHED_GOOD` / `SUBASSEMBLY` / `RAW_MATERIAL`.
- **Bill of Materials** — two-tier `MANUF_COMPONENT` structure.
- **Machines** — two production machines (`AMC`, `LAM`) as fixed assets, each with a
  work calendar (`AMC` 5-day week, `LAM` 3-day week).
- **Routings** — routings and operations tied to the products they produce, including
  a stable alternate-deck routing baseline for operation maintenance flows.
- **Shop-floor references** — a worker party, routing labor cost calculation rule,
  task output, tool standard and operator assignment, so routing-cost screens work
  without the stock `demo` reader.
- **Purchasing** — five suppliers and the vendor relationships for the raw materials.

## Load it

Self-contained — load it on `seed` + `seed-initial` only (no stock `demo` needed), on the
custom `manufacturingDemo` reader:

```bash
# from the framework root
./gradlew "ofbiz --load-data readers=seed,seed-initial,manufacturingDemo"
```

Then start OFBiz and log in to the Manufacturing application with `admin` / `ofbiz`. The
component auto-registers (it contains an `ofbiz-component.xml`); restart OFBiz to pick it up.

## Roadmap

Later slices add the transactional and analytical layers on top of this configuration:
variant/virtual-product BOMs, work-calendar exceptions, standalone production runs,
sales forecasts + MRP execution, and weighted-average material + actual production
costing (the published `$231`/unit worked example).
