package com.kpd.kpd_bot.message_services;

import com.kpd.kpd_bot.api.QuoteAPI;
import com.kpd.kpd_bot.dto.response.BaseQuoteResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuoteAdapter implements Adapter {
	private final QuoteAPI quoteAPI;

	@Override
	public String getTextFromMessageService() {
		BaseQuoteResponseDTO responseDTO = quoteAPI.getQuote();
		return this.formatFromObjectToText(responseDTO);
	}

	private String formatFromObjectToText(BaseQuoteResponseDTO dto) {
		StringBuilder sb = new StringBuilder();
		sb.append("Цитата дня:\n")
				.append(dto.getA()).append("\n")
				.append(dto.getQ());

		return sb.toString();
	}
}
