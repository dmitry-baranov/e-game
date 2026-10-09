package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import ru.itis.diploma.model.TradingSessionResults;

import java.math.BigDecimal;
import java.util.List;

public interface TradingSessionResultsRepository extends JpaRepository<TradingSessionResults, Long> {

    @Query(nativeQuery = true, value = "select sum(price * product_number) from trading_session_results " +
        "where manufacturer_id = :manufacturerId and trade_date > :from and trade_date <= :to")
    BigDecimal getPurchasesTotal(Long manufacturerId, Integer from, Integer to);

    List<TradingSessionResults> findByManufacturerId(Long manufacturerId);

    @Query("SELECT SUM(t.productNumber) FROM TradingSessionResults t WHERE t.manufacturer.game.id = :gameId AND t.isWornOut = false")
    Integer getUnwornProductsCountByGameId(@Param("gameId") Long gameId);

    List<TradingSessionResults> findByTradeDateGreaterThanEqualAndManufacturerIdIn(int startDate, List<Long> manufacturerIds);

    @Query("SELECT t.manufacturer.id, SUM(t.productNumber) FROM TradingSessionResults t " +
        "WHERE t.tradeDate >= :startDate AND t.manufacturer.id IN :manufacturerIds " +
        "GROUP BY t.manufacturer.id")
    List<Object[]> sumProductsByManufacturerSince(@Param("startDate") int startDate,
                                                    @Param("manufacturerIds") List<Long> manufacturerIds);

    @Query("SELECT t FROM TradingSessionResults t WHERE t.productNumber > 0")
    List<TradingSessionResults> findAllByProductNumberGreaterThanZero();

    @Query("SELECT t FROM TradingSessionResults t WHERE t.manufacturer.game.id = :gameId AND t.productNumber > 0 AND t.isWornOut = false")
    List<TradingSessionResults> findUnwornPurchasesByGameId(@Param("gameId") Long gameId);

    @Modifying
    @Query(value = """
        UPDATE trading_session_results t SET is_worn_out = true
        FROM manufacturer m
        WHERE t.manufacturer_id = m.id AND m.game_id = :gameId
          AND t.product_number > 0 AND t.is_worn_out = false
          AND (:day - t.trade_date) >= t.quality_index * :lifetime
        """, nativeQuery = true)
    int markWornOutByGameId(@Param("gameId") Long gameId, @Param("day") int day,
                            @Param("lifetime") int lifetime);
}
