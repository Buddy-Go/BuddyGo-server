-- 공지 댓글 답글(1단계). parent_id가 있으면 답글이고, 부모는 항상 parent_id가 없는 댓글이다(서버에서 검사).
-- 답글이 달린 댓글을 지우면 행은 남기고 deleted_at을 찍고 content를 비운다(답글이 없으면 실제 삭제).
-- 자기 참조 FK는 NO ACTION: 공지·모임 삭제의 CASCADE가 부모와 답글을 한 문장에서 지울 때는 문장 끝에 검사하므로 통과하고,
-- 답글이 남은 부모를 실수로 실제 삭제하면 막는다.
ALTER TABLE notice_comments
    ADD COLUMN parent_id  BIGINT REFERENCES notice_comments (id),
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN content DROP NOT NULL,
    ADD CONSTRAINT ck_notice_comments_deleted_content CHECK ((deleted_at IS NULL) = (content IS NOT NULL));

CREATE INDEX idx_notice_comments_parent ON notice_comments (parent_id) WHERE parent_id IS NOT NULL;
