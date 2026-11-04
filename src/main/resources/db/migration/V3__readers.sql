-- Readers: network-wide membership with a card number from a sequence.
-- No check on category: the set of categories lives in configuration (library.readers.categories).

create sequence reader_card_number_seq start with 1;

create table reader (
    id            bigint generated always as identity primary key,
    card_number   varchar(20)  not null,
    first_name    varchar(100) not null,
    last_name     varchar(100) not null,
    date_of_birth date         not null,
    category      varchar(20)  not null,
    email         varchar(254),
    phone         varchar(20),
    version       bigint       not null default 0,
    constraint uk_reader_card_number unique (card_number),
    constraint ck_reader_contact check (email is not null or phone is not null)
);

create index ix_reader_last_name on reader (lower(last_name));
create index ix_reader_category on reader (category);
