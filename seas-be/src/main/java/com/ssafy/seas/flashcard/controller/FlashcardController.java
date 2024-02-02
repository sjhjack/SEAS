package com.ssafy.seas.flashcard.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.seas.category.dto.CategoryDto;
import com.ssafy.seas.category.service.CategoryService;
import com.ssafy.seas.common.constants.ErrorCode;
import com.ssafy.seas.common.constants.SuccessCode;
import com.ssafy.seas.common.dto.ApiResponse;
import com.ssafy.seas.flashcard.dto.FlashcardDto;
import com.ssafy.seas.flashcard.service.FlashcardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FlashcardController {
	private final FlashcardService flashcardService;
	private final CategoryService categoryService;

	@GetMapping("/flashcard")
	public ApiResponse<List<FlashcardDto.Response>> getFlashcards(@RequestParam("category") String categoryName) {
		List<CategoryDto.Response> categories = categoryService.getCategories();

		// categoryName 검증: categoryName과 일치하는 카테고리가 존재하지 않는다면
		if (categories.stream().noneMatch(category -> categoryName.equals(category.getName()))) {
			throw new NoSuchElementException(ErrorCode.BAD_CATEGORY_NAME.getMessage());
		}
		return ApiResponse.success(SuccessCode.GET_SUCCESS,
			flashcardService.getFlashcaradsByCategoryName(categoryName));
	}
}