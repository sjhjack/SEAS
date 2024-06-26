package com.ssafy.seas.common.logging;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.seas.common.dto.ApiResponse;
import com.ssafy.seas.common.exception.CustomException;
import com.ssafy.seas.common.exception.ExceptionUtil;
import com.ssafy.seas.common.exception.TokenException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter extends OncePerRequestFilter {
	protected static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
	private final DiscordNotifier discordNotifier;
	private StringBuilder stringBuilder = new StringBuilder();
	private boolean isSwagger = false;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		stringBuilder.setLength(0);

		stringBuilder.append("time: ").append(LocalDateTime.now()).append("\n");
		MDC.put("traceId", UUID.randomUUID().toString());
		try {
			if (isAsyncDispatch(request)) {
				filterChain.doFilter(request, response);
			} else {
				doFilterWrapped(new RequestWrapper(request), new ResponseWrapper(response), filterChain);
			}
		} catch (Exception ex) {
			handleException(ex, response);
		} finally {
			stringBuilder.append("✨========================✨");
			discordNotifier.notify(stringBuilder.toString());
			MDC.clear();
		}
	}

	protected void doFilterWrapped(RequestWrapper request, ContentCachingResponseWrapper response,
		FilterChain filterChain) throws ServletException, IOException {
		try {
			logRequest(request);
			filterChain.doFilter(request, response);
		} catch (Exception ex) {
			handleException(ex, response);
		} finally {
			logResponse(response);
			response.copyBodyToResponse();
		}
	}

	private void logRequest(RequestWrapper request) throws IOException {
		String queryString = request.getQueryString();
		String AccessControlRequestHeaders = request.getHeader("access-control-request-headers");
		String logMessage = String.format(
			"Request : %s uri=[%s] content-type=[%s] AccessControlRequestHeaders = [%s] Authorization=[%s] Origin=[%s]\n",
			request.getMethod(),
			queryString == null ? request.getRequestURI() : request.getRequestURI() + queryString,
			request.getContentType(), AccessControlRequestHeaders, request.getHeader("Authorization"),
			request.getHeader("Origin"));

		log.info(logMessage);
		stringBuilder.append(logMessage).append("\n");

		isSwagger = false;
		String[] swaggerUris = {"swagger", "api-docs"};
		for (String swaggerUri : swaggerUris) {
			if (request.getRequestURI().contains(swaggerUri)) {
				isSwagger = true;
				break;
			}
		}

		logPayload("Request", request.getContentType(), request.getInputStream());

	}

	private void logResponse(ContentCachingResponseWrapper response) throws IOException {
		String logMessage = String.format("Response : %s", response.getStatus());
		stringBuilder.append(logMessage).append("\n");
		if (!isSwagger) {
			logPayload("Response", response.getContentType(), response.getContentInputStream());
		}
	}

	private void logPayload(String prefix, String contentType, InputStream inputStream) throws IOException {
		boolean visible = isVisible(MediaType.valueOf(contentType == null ? "application/json" : contentType));
		if (visible) {
			byte[] content = StreamUtils.copyToByteArray(inputStream);
			if (content.length > 0) {
				String contentString = new String(content);
				log.info("{} Payload: {}", prefix, contentString);
				stringBuilder.append(prefix).append(" Payload:").append(contentString).append("\n");

			}
		} else {
			log.info("{} Payload: Binary Content", prefix);
			stringBuilder.append(prefix).append(" Payload: Binary Content").append("\n");
		}
	}

	private boolean isVisible(MediaType mediaType) {
		final List<MediaType> VISIBLE_TYPES = Arrays.asList(MediaType.valueOf("text/*"),
			MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
			MediaType.valueOf("application/*+json"), MediaType.valueOf("application/*+xml"),
			MediaType.MULTIPART_FORM_DATA);
		return VISIBLE_TYPES.stream().anyMatch(visibleType -> visibleType.includes(mediaType));
	}

	private void handleException(Exception ex, HttpServletResponse response) throws IOException {
		log.error("Exception during request processing", ex);

		String logMessage = String.format("[ERROR] : %s", ex.getMessage() + "\n\n");
		stringBuilder.append(logMessage).append("\n");

		if (!(ex instanceof CustomException)) {
			stringBuilder.append("🚨 Exception 발생! 🚨\n");
			stringBuilder.append(ExceptionUtil.exceptionToString(ex)).append("\n");
		}

		ApiResponse<?> errorResponse = ApiResponse.error(HttpStatus.BAD_REQUEST, ex.getMessage());
		ObjectMapper objectMapper = new ObjectMapper();
		String jsonResponse = objectMapper.writeValueAsString(errorResponse);
		// Customize the response based on the exception
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(jsonResponse);
	}
}
