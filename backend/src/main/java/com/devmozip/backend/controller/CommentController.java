package com.devmozip.backend.controller;

import com.devmozip.backend.dto.feed.CommentRequest;
import com.devmozip.backend.dto.feed.CommentResponse;
import com.devmozip.backend.dto.feed.FeedReportRequest;
import com.devmozip.backend.dto.feed.FeedReportResponse;
import com.devmozip.backend.service.CommentService;
import com.devmozip.backend.service.FeedReportService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** WBS 8.11, 8.12, 8.16. 모두 로그인이 필요하다. */
@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {
	private static final String ID = "/{id:[0-9]{1,18}}";

	private final CommentService comments;
	private final FeedReportService reports;

	public CommentController(CommentService comments, FeedReportService reports) {
		this.comments = comments;
		this.reports = reports;
	}

	@PutMapping(ID)
	public CommentResponse update(@PathVariable long id, @RequestBody CommentRequest request) {
		return comments.update(id, request.content());
	}

	@DeleteMapping(ID)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		comments.delete(id);
	}

	@PostMapping(ID + "/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public FeedReportResponse report(@PathVariable long id, @RequestBody FeedReportRequest request) {
		return reports.reportComment(id, request.reason());
	}
}
