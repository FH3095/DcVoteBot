
CREATE TABLE default_settings (
  serverId bigint(20) NOT NULL,
  votesPerVoter tinyint(4) NOT NULL,
  durationSeconds bigint(20) NOT NULL,
  voterCanChangeVotes tinyint(1) NOT NULL,
  timezoneId varchar(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE to_update_votes (
  serverId bigint(20) NOT NULL,
  messageId bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE voters_votes (
  voteOptionId bigint(20) NOT NULL,
  voterId bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE votes (
  serverId bigint(20) NOT NULL,
  messageId bigint(20) NOT NULL,
  channelId bigint(20) NOT NULL,
  title varchar(100) NOT NULL,
  description varchar(1000) NOT NULL,
  start bigint(20) NOT NULL,
  lastEditMade tinyint(1) NOT NULL DEFAULT 0,
  votesPerVoter tinyint(4) NOT NULL,
  durationSeconds bigint(20) NOT NULL,
  voterCanChangeVotes tinyint(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE vote_options (
  id bigint(20) NOT NULL,
  messageId bigint(20) NOT NULL,
  title varchar(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


ALTER TABLE default_settings
  ADD PRIMARY KEY (serverId);

ALTER TABLE to_update_votes
  ADD PRIMARY KEY (messageId);
ALTER TABLE to_update_votes
  ADD KEY idx_to_update_votes_serverId (serverId);

ALTER TABLE voters_votes
  ADD PRIMARY KEY (voteOptionId,voterId);
ALTER TABLE voters_votes
  ADD KEY idx_voters_votes_voteOptionId (voteOptionId);
ALTER TABLE voters_votes
  ADD KEY idx_voters_votes_voterId (voterId);

ALTER TABLE votes
  ADD PRIMARY KEY (messageId);
ALTER TABLE votes
  ADD KEY idx_votes_serverId (serverId);
ALTER TABLE votes
  ADD KEY idx_votes_lastEditMade (lastEditMade);

ALTER TABLE vote_options
  ADD PRIMARY KEY (id);
ALTER TABLE vote_options
  ADD UNIQUE KEY idx_vote_options_messageId_title (messageId,title);
ALTER TABLE vote_options
  ADD KEY idx_vote_options_messageId (messageId);


ALTER TABLE vote_options
  MODIFY id bigint(20) NOT NULL AUTO_INCREMENT;


ALTER TABLE voters_votes
  ADD CONSTRAINT fk_voters_votes_2_vote_option FOREIGN KEY (voteOptionId) REFERENCES vote_options (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE vote_options
  ADD CONSTRAINT fk_vote_options_2_vote FOREIGN KEY (messageId) REFERENCES votes (messageId) ON DELETE CASCADE ON UPDATE CASCADE;
