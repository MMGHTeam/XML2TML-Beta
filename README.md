# XML to TML — Spring Boot + Vaadin

## Features
- Sign-in protected Vaadin UI (Spring Security)
- H2 in-memory database with a seeded demo user
- Upload a `GROUP` XML file containing `TX` and `RX` sections
- Preview fields and export an `.xlsx` workbook with **TX Request** and **RX Response** sheets
- Excel layout follows the attached reference's six-column table style: From, To, Length, Data Type, Description, Value

## Requirements
- Java 21
- Maven 3.9+

## Run
```bash
mvn spring-boot:run
```
Open http://localhost:8080

Demo credentials (change immediately for any non-demo use):
- Username: `admin`
- Password: `ChangeMe123!`

## XML mapping rules
- `9(n)` and `X(n)` lengths are `n`; `9(n)VmmmS` length is stored digits plus decimal digits (`n + mmm`)
- `yyyyMMdd` and `ddMMyyyy` are length 8
- Numeric formats map to `Numeric`, X formats to `Alphanumeric`, date formats to `Date`
- Offsets are calculated sequentially from 1 independently for TX and RX.
- The XML field ID and original format are combined in the Excel Description cell. The Value column is blank for specification entry.
- Unsupported formats are rejected with a visible error.

