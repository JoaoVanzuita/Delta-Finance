-- Permite manter uma posição manual como referência sem contá-la no patrimônio.
alter table investment_position
    add column exclude_from_wealth boolean default false not null;

comment on column investment_position.exclude_from_wealth is
    'Quando verdadeiro, a posição manual permanece visível mas não compõe o patrimônio.';
