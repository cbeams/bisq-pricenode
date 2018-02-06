/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.price.spot;

import bisq.price.spot.providers.AbstractExchangeRateProvider;
import bisq.price.spot.providers.BitcoinAverage;
import bisq.price.spot.providers.CoinMarketCap;
import bisq.price.spot.providers.Poloniex;

import java.time.Instant;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExchangeRateService {

    private static final long MARKET_PRICE_TTL_SEC = 1800; // 30 min

    private final Set<ExchangeRateProvider> providers;

    public ExchangeRateService(BitcoinAverage.Local bitcoinAverageLocal,
                               BitcoinAverage.Global bitcoinAverageGlobal,
                               Poloniex poloniex,
                               CoinMarketCap coinMarketCap) {

        this.providers = new LinkedHashSet<ExchangeRateProvider>() {{
            add(bitcoinAverageGlobal);
            add(bitcoinAverageLocal);
            add(coinMarketCap);
            add(poloniex);
        }};
    }

    public void start() throws Exception {
        for (ExchangeRateProvider provider : providers) {
            if (provider instanceof AbstractExchangeRateProvider) {
                ((AbstractExchangeRateProvider) provider).start();
            }
        }
    }

    public Map<String, Object> getAllMarketPrices() {

        Map<String, Object> allMarketPrices = new LinkedHashMap<>();
        Map<String, ExchangeRateData> allData = new HashMap<>();

        for (ExchangeRateProvider exchangeRateProvider : providers) {
            Map<? extends String, ? extends ExchangeRateData> data = exchangeRateProvider.getData();
            Collection<? extends ExchangeRateData> prices = data.values();

            String debugPrefix = exchangeRateProvider.getDebugPrefix();
            long count = prices.size();
            long timestamp = prices.stream()
                    .filter(e -> exchangeRateProvider.getProviderSymbol().equals(e.getProvider()))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "No exchange rate data found for " + exchangeRateProvider))
                    .getTimestampSec();

            if (exchangeRateProvider instanceof BitcoinAverage.Local) {
                allMarketPrices.put("btcAverageTs", timestamp);
            }

            allMarketPrices.put(debugPrefix + "Ts", timestamp);
            allMarketPrices.put(debugPrefix + "Count", count);

            allData.putAll(data);
        }

        allMarketPrices.put("data", removeOutdatedPrices(allData).values().toArray());

        return allMarketPrices;
    }

    private Map<String, ExchangeRateData> removeOutdatedPrices(Map<String, ExchangeRateData> map) {
        long now = Instant.now().getEpochSecond();
        long limit = now - MARKET_PRICE_TTL_SEC;
        return map.entrySet().stream()
                .filter(e -> e.getValue().getTimestampSec() > limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
