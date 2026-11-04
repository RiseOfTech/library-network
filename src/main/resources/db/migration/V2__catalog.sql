-- Catalog: branches, books with ordered authors, physical copies.
-- Constraint names are referenced from the code to map uniqueness races to API errors.

create table branch (
    id      bigint generated always as identity primary key,
    code    varchar(20)  not null,
    name    varchar(200) not null,
    address varchar(500) not null,
    constraint uk_branch_code unique (code)
);

create table book (
    id               bigint generated always as identity primary key,
    title            varchar(500) not null,
    isbn             char(13)     not null,
    publication_year integer,
    constraint uk_book_isbn unique (isbn)
);

create index ix_book_title_lower on book (lower(title));

create table book_author (
    book_id  bigint       not null,
    position integer      not null,
    name     varchar(200) not null,
    constraint pk_book_author primary key (book_id, position),
    constraint fk_book_author_book foreign key (book_id) references book (id)
);

create table copy (
    id               bigint generated always as identity primary key,
    book_id          bigint      not null,
    branch_id        bigint      not null,
    inventory_number varchar(30) not null,
    status           varchar(20) not null,
    version          bigint      not null default 0,
    constraint uk_copy_inventory_number unique (inventory_number),
    constraint fk_copy_book foreign key (book_id) references book (id),
    constraint fk_copy_branch foreign key (branch_id) references branch (id),
    constraint ck_copy_status check (status in ('ON_SHELF', 'ON_LOAN', 'ON_HOLD', 'IN_TRANSIT', 'LOST'))
);

create index ix_copy_book_branch on copy (book_id, branch_id);
create index ix_copy_status on copy (status);
