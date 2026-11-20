package com.uel.homehelp.catalog;

import org.springframework.http.HttpStatus;

import com.uel.homehelp.common.web.ApiException;

/** {@code minPrice} is greater than {@code maxPrice}. */
class InvalidPriceRangeException extends ApiException {

	InvalidPriceRangeException() {
		super(HttpStatus.BAD_REQUEST, "invalid-price-range", "Invalid price range",
				"minPrice must not be greater than maxPrice.");
	}

}
