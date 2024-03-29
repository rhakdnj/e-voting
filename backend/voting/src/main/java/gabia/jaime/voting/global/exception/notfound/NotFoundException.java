package gabia.jaime.voting.global.exception.notfound;

import gabia.jaime.voting.global.exception.CustomException;
import org.springframework.http.HttpStatus;

public class NotFoundException extends CustomException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
