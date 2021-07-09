begin;

create unique index idx_strbd_gi_smi
    on starboard(guild_id, source_message_id);

commit;
