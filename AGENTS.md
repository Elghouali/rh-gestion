# RH project
Read README.md, docs/STATUS.md and docs/architecture/conception-v2.md before changes.
- Java/Spring Boot microservices and Angular; no Laravel/React backend.
- Preserve database ownership. No joins or direct SQL access between services.
- Money: BigDecimal; technical dates: UTC; business display: Africa/Casablanca.
- Never fabricate production stock, sales, financial balances or legal settings.
- No hardcoded credentials. .env, tokens and authorization storage stay untracked.
- Keep FR/AR/EN and RTL usable.
- Authorization must be enforced in backend query/application services, including exports.
- Business documents and stock/financial journals must eventually be immutable with compensations.
- Tests: Maven verify, Angular unit tests/build, Playwright after auth changes.
- Do not remove volumes or overwrite local secrets to fix an ordinary code failure.
- Update docs/STATUS.md and acceptance evidence with each delivered milestone.
