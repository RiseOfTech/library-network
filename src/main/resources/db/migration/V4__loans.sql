-- Loans: checkout and return of copies. Status is derived: open while returned_at is null.
-- uk_loan_open_copy is a safety net for "at most one open loan per copy"; the loan limit has no
-- database guard (it depends on configuration) and is protected by a row lock on the reader.

create table loan (
    id               bigint generated always as identity primary key,
    reader_id        bigint      not null,
    copy_id          bigint      not null,
    branch_id        bigint      not null,
    checked_out_at   timestamptz not null,
    loan_period_days integer     not null,
    due_date         date        not null,
    returned_at      timestamptz,
    version          bigint      not null default 0,
    constraint fk_loan_reader foreign key (reader_id) references reader (id),
    constraint fk_loan_copy   foreign key (copy_id)   references copy (id),
    constraint fk_loan_branch foreign key (branch_id) references branch (id),
    constraint ck_loan_period_positive check (loan_period_days > 0),
    constraint ck_loan_returned_after_checkout check (returned_at is null or returned_at >= checked_out_at)
);

create unique index uk_loan_open_copy on loan (copy_id) where returned_at is null;
create index ix_loan_reader_open on loan (reader_id) where returned_at is null;
create index ix_loan_reader on loan (reader_id, checked_out_at desc);
create index ix_loan_copy on loan (copy_id, checked_out_at desc);
