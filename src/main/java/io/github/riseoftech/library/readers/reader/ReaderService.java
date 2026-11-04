package io.github.riseoftech.library.readers.reader;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.riseoftech.library.common.LikePatterns;
import io.github.riseoftech.library.common.PageResponse;
import io.github.riseoftech.library.readers.category.ReaderCategories;
import io.github.riseoftech.library.readers.category.ReaderCategory;
import io.github.riseoftech.library.readers.reader.ReaderDtos.ReaderRequest;
import io.github.riseoftech.library.readers.reader.ReaderDtos.ReaderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReaderService {

    private static final Pattern CARD_NUMBER = Pattern.compile("(?i)^LN-\\d+$");

    private static final Sort BY_NAME = Sort.by("lastName", "firstName", "id");

    private final ReaderRepository readers;
    private final ReaderCategories categories;
    private final Clock clock;

    public ReaderService(ReaderRepository readers, ReaderCategories categories, Clock clock) {
        this.readers = readers;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public ReaderResponse register(ReaderRequest request) {
        checkCategoryAllowed(request.category(), request.dateOfBirth());
        Reader reader = new Reader(formatCardNumber(readers.nextCardNumber()),
                request.firstName(), request.lastName(), request.dateOfBirth(), request.category(),
                blankToNull(request.email()), blankToNull(request.phone()));
        return ReaderResponse.from(readers.saveAndFlush(reader));
    }

    /**
     * Replaces everything except the card number. The category age limit is checked again only
     * when the category or the date of birth changes.
     */
    @Transactional
    public ReaderResponse update(long id, ReaderRequest request) {
        Reader reader = load(id);
        if (!reader.getCategory().equals(request.category())
                || !reader.getDateOfBirth().equals(request.dateOfBirth())) {
            checkCategoryAllowed(request.category(), request.dateOfBirth());
        }
        reader.replace(request.firstName(), request.lastName(), request.dateOfBirth(), request.category(),
                blankToNull(request.email()), blankToNull(request.phone()));
        return ReaderResponse.from(readers.saveAndFlush(reader));
    }

    public ReaderResponse get(long id) {
        return ReaderResponse.from(load(id));
    }

    /**
     * A query in the card number form matches that card exactly; anything else is a case-insensitive
     * substring of the first name, last name or full name. A blank query has no name filter.
     * {@code category} {@code null} means any category.
     */
    public PageResponse<ReaderResponse> search(String query, String category, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, BY_NAME);
        String q = query == null ? "" : query.strip();
        Page<Reader> result;
        if (isCardNumber(q)) {
            result = readers.findByCardNumber(q.toUpperCase(Locale.ROOT), category, pageRequest);
        } else {
            result = readers.search(q.isEmpty() ? null : LikePatterns.contains(q), category, pageRequest);
        }
        return PageResponse.from(result, ReaderResponse::from);
    }

    /** {@code LN-} and the number zero-padded to at least 6 digits. */
    static String formatCardNumber(long number) {
        return "LN-%06d".formatted(number);
    }

    static boolean isCardNumber(String query) {
        return query != null && CARD_NUMBER.matcher(query).matches();
    }

    private void checkCategoryAllowed(String code, LocalDate dateOfBirth) {
        ReaderCategory category = categories.find(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reader category " + code));
        if (!categories.isAllowedFor(category, dateOfBirth, LocalDate.now(clock))) {
            throw new ReaderCategoryNotAllowedException(code, dateOfBirth);
        }
    }

    private Reader load(long id) {
        return readers.findById(id).orElseThrow(() -> new ReaderNotFoundException(id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
