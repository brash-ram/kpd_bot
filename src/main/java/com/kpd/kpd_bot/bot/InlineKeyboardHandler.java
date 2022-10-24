package com.kpd.kpd_bot.bot;

import com.kpd.kpd_bot.entity.*;
import com.kpd.kpd_bot.myenum.UserStateEnum;
import com.kpd.kpd_bot.service.*;
import com.kpd.kpd_bot.statics.Buttons;
import com.kpd.kpd_bot.statics.StringConst;
import com.kpd.kpd_bot.util.InlineKeyboardConstructor;
import com.kpd.kpd_bot.util.SettingSubscriptionsKeyboard;
import com.kpd.kpd_bot.util.TimeSendInlineKeyboardHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InlineKeyboardHandler {
	private final TimeSendInlineKeyboardHandler timeSendInlineKeyboardHandler;
	private final SubscriptionService subscriptionService;
	private final ExchangeRatesSettingService exchangeRatesSettingService;
	private final UserService userService;
	private final SettingService settingService;
	private final UserStateService userStateService;
	private final List<String> listSubscriptions = new ArrayList<String>(Arrays.asList("weather", "quote", "film", "exchangeRates", "news"));

	public void handleMessage(Update update, Bot bot) throws TelegramApiException {
		String callData = update.getCallbackQuery().getData();
		int messageId = update.getCallbackQuery().getMessage().getMessageId();
		long chatId = update.getCallbackQuery().getMessage().getChatId();
		String messageText = update.getCallbackQuery().getMessage().getText();
		Long userId = update.getCallbackQuery().getFrom().getId();
		UserInfo userInfo = userService.findById(userId);
		MessageAdapter newMessage = null;
		DeleteMessage deleteMessage = null;
		EditMessageText editMessage = this.editMessage(chatId, messageId, messageText, update.getCallbackQuery().getMessage().getReplyMarkup());

		switch (callData) {
			case "<<" ->
				editMessage = this.editMessage(chatId, messageId,
						timeSendInlineKeyboardHandler.subHour(messageText), update.getCallbackQuery().getMessage().getReplyMarkup());
			case ">>" ->
				editMessage = this.editMessage(chatId, messageId,
						timeSendInlineKeyboardHandler.addHour(messageText), update.getCallbackQuery().getMessage().getReplyMarkup());

			case "backSetting" -> {
				editMessage = this.editMessage(chatId, messageId, StringConst.SETTINGS_MESSAGE, Buttons.getSettingButtons());
				this.clearUserState(chatId);
			}

			case "backSubscription", "setMessageInfoParameters" -> editMessage = this.editMessage(chatId, messageId, StringConst.NEWS_PARAMETERS_MESSAGE,
					SettingSubscriptionsKeyboard.createInlineKeyboardSettingSubscription(userInfo.getSubscription()));

			case "setTimeSend" -> {
				settingService.saveSetting(userInfo.getUserSetting().setTimeSend(messageText));
				editMessage = this.editMessage(chatId, messageId, StringConst.SETTINGS_MESSAGE, Buttons.getSettingButtons());
				newMessage = new MessageAdapter().setChatId(chatId).setText(StringConst.SUCCESSFULLY_SET_TIME_SEND);
			}

			case "setSendingMessageTime" -> {
				String currentTimeSend = userInfo.getUserSetting().getTimeSend();
				editMessage = this.editMessage(chatId, messageId, currentTimeSend,
						new InlineKeyboardConstructor()
								.addInlineButtonInRow("<<", "<<")
								.addInlineButtonInRow(">>", ">>")
								.addNewInlineRow().addInlineButtonInRow("Подтвердить", "setTimeSend")
								.getInlineKeyboard()
				);
			}

			case "setUserForm" -> {
				userStateService.saveUserState(userId, UserStateEnum.WAIT_NAME);
				editMessage = this.editMessage(chatId, messageId, StringConst.INPUT_NAME_FOR_USER,
						new InlineKeyboardConstructor().addInlineButtonInRow(StringConst.BACK, "backSetting")
								.getInlineKeyboard());
			}

			case "setUserCity" -> {
				editMessage = this.editMessage(chatId, messageId, StringConst.INPUT_CITY_FOR_USER,
						new InlineKeyboardConstructor().addInlineButtonInRow(StringConst.BACK, "backSubscription")
						.getInlineKeyboard());
				if (!userStateService.existByUserId(userId)) {
					userStateService.saveUserState(userId, UserStateEnum.WAIT_CITY);
				}
			}

			case "setCurrencies" -> {
				editMessage.setText(StringConst.SET_CURRENCIES);
				this.handleExchangeRatesSetting(callData, userId, editMessage, userInfo.getExchangeRatesSetting());
			}

			case "setNewscategory" -> {
				editMessage.setText(StringConst.SET_NEWS_CATEGORY);
				this.handleNewsCategorySetting(callData, userId, editMessage, userInfo.getUserSetting());
			}


			default ->{
				if (listSubscriptions.contains(callData)) {
					this.handleSettingSubscription(callData, userId, editMessage, userInfo.getSubscription());
				} else if (StringConst.NEWS_CATEGORIES.containsKey(callData)) {
					this.handleNewsCategorySetting(callData, userId, editMessage, userInfo.getUserSetting());
				} else {
					this.handleExchangeRatesSetting(callData, userId, editMessage, userInfo.getExchangeRatesSetting());
				}
			}
	}

		if (newMessage != null) {
			bot.execute(newMessage.getSendMessage());
		}

		if (editMessage != null) {
			bot.execute(editMessage);
		}

		if (deleteMessage != null) {
			bot.execute(deleteMessage);
		}

	}

	private EditMessageText handleSettingSubscription(String field, Long userId, EditMessageText editMessage, Subscription subscription) {
		switch (field) {
			case "weather" -> subscription = subscription.setWeather(!subscription.getWeather());
			case "quote" -> subscription = subscription.setQuote(!subscription.getQuote());
			case "film" -> subscription = subscription.setFilm(!subscription.getFilm());
			case "exchangeRates" -> subscription = subscription.setExchangeRates(!subscription.getExchangeRates());
			case "news" -> subscription = subscription.setNews(!subscription.getNews());
		}
		subscriptionService.saveSubscription(subscription);
		editMessage.setReplyMarkup(SettingSubscriptionsKeyboard.createInlineKeyboardSettingSubscription(subscription));
		return editMessage;
	}

	private EditMessageText handleExchangeRatesSetting(String field, Long userId, EditMessageText editMessage, ExchangeRatesSetting exchangeRatesSetting) {
		switch (field) {
			case "CHF/RUB" -> exchangeRatesSetting = exchangeRatesSetting.setCHF_RUB(!exchangeRatesSetting.getCHF_RUB());
			case "JPY/RUB" -> exchangeRatesSetting = exchangeRatesSetting.setJPY_RUB(!exchangeRatesSetting.getJPY_RUB());
			case "EUR/RUB" -> exchangeRatesSetting = exchangeRatesSetting.setEUR_RUB(!exchangeRatesSetting.getEUR_RUB());
			case "GBP/RUB" -> exchangeRatesSetting = exchangeRatesSetting.setCNY_RUB(!exchangeRatesSetting.getCNY_RUB());
			case "USD/RUB" -> exchangeRatesSetting = exchangeRatesSetting.setUSD_RUB(!exchangeRatesSetting.getUSD_RUB());
		}
		exchangeRatesSettingService.saveExchangeRatesSetting(exchangeRatesSetting);
		editMessage.setReplyMarkup(SettingSubscriptionsKeyboard.createInlineKeyboardExchangeRatesSetting(exchangeRatesSetting));
		return editMessage;
	}

	private EditMessageText handleNewsCategorySetting(String field, Long userId, EditMessageText editMessage, UserSetting setting) {
		StringConst.NEWS_CATEGORIES.keySet().forEach(key -> {
			if (key.equals(field)) {
				setting.setNewsCategory(key);
			}
		});
		settingService.saveSetting(setting);
		editMessage.setReplyMarkup(SettingSubscriptionsKeyboard.createInlineKeyboardNewsCategorySetting(setting));
		return editMessage;
	}


	private EditMessageText editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup keyboardMarkup) {
		EditMessageText editMessage = new EditMessageText();
		editMessage.setChatId(chatId);
		editMessage.setMessageId(messageId);
		editMessage.setText(text);
		editMessage.setReplyMarkup(keyboardMarkup);
		return editMessage;
	}

	private DeleteMessage deleteMessage(long chatId, int messageId) {
		DeleteMessage deleteMessage = new DeleteMessage();
		deleteMessage.setChatId(chatId);
		deleteMessage.setMessageId(messageId);
		return deleteMessage;
	}

	private void clearUserState(Long userId) {
		UserState userState = userStateService.findUserState(userId);
		if (userState != null) {
			userStateService.deleteUserState(userId);
		}
	}
}
