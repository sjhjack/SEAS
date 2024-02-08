package com.ssafy.seas.quiz.util;

import com.ssafy.seas.quiz.dto.QuizAnswerDto;
import com.ssafy.seas.quiz.dto.QuizDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Configuration
@Slf4j
@RequiredArgsConstructor
public class QuizUtil {

    private final RedisTemplate<Integer, Map<Integer, QuizDto.QuizFactorDto>> redisTemplate;

//    public QuizUtil(RedisTemplate<Integer, Map<Integer, QuizDto.QuizFactorDto>> redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }

    public double[][] getPrefixWeightArray(List<QuizDto.QuizWeightInfoDto> weightInfo){

        int size = weightInfo.size();
        double[][] quizWeight = new double[size][2];

        quizWeight[0][0] = weightInfo.get(0).getQuizId();
        quizWeight[0][1] = toFixed(1 / (weightInfo.get(0).getQuizInterval() * weightInfo.get(0).getEf()), 1);

        for(int factorIndex = 1; factorIndex < size; factorIndex++){
            QuizDto.QuizWeightInfoDto quizWeightInfo = weightInfo.get(factorIndex);

            int quizId = quizWeightInfo.getQuizId();
            double interval = quizWeightInfo.getQuizInterval();
            double ef = quizWeightInfo.getEf();

            Double weight = interval * ef;
            quizWeight[factorIndex][0] = toFixed(quizId, 1);
            // weight가 작을 수록 많이 뽑혀야 하므로, weight를 역수로 치환
            quizWeight[factorIndex][1] = toFixed((quizWeight[factorIndex - 1][1]) + 1 / weight, 1);
        }

        return quizWeight;
    }


    // quizzes의 배열은 {quizId, ef}로 이루어짐

    public double[] selectQuizzes(double[][] quizWeightInfo) {

        double[] selectedQuizInfo = new double[3];

        int size = quizWeightInfo.length;

        // 2차원 배열에서 weight 값만 뽑아낸다.
        double[] weightArray = Arrays.stream(quizWeightInfo).mapToDouble(row -> row[1]).toArray();
        checkWeightArray(weightArray);

        double max = weightArray[size - 1];
        double min = weightArray[0];
        
        // 1.3 ~ max 구간의 랜덤값이 나옴
        double random = toFixed(Math.random() * (max - min) + min, 1);
        log.info("RAMDON : {} ", random);
        int selectedQuizIndex = Arrays.binarySearch(weightArray, random);

        // binarySearch에서는 정확한 값이 아니면, 음수로 (- (이 값이 배열에 있을 시 위치할 인덱스 + 1))를 돌려주므로 절댓값 - 1으로 해준다.
        selectedQuizIndex = selectedQuizIndex < 0 ? Math.abs(selectedQuizIndex) - 1 : selectedQuizIndex;
        selectedQuizInfo[0] = quizWeightInfo[selectedQuizIndex][0];
        selectedQuizInfo[1] = quizWeightInfo[selectedQuizIndex][1]; // FIX : 이후 삭제

        // factor 리스트에서 weight를 초기화시켜주기 위해 index 값을 담음
        selectedQuizInfo[2] = selectedQuizIndex * 1.0;

        return selectedQuizInfo;
    }

    public void updateHintState(Integer memberId, Integer quizId){
        Map<Integer, QuizDto.QuizFactorDto> value = redisTemplate.opsForValue().get(memberId);

        value.get(quizId).setUsedHint(true);

        log.info("updateHintState : " + quizId + " || " + value.get(quizId).getQuiz());
    }

    public void updateQuizState(Integer memberId, Integer quizId){
        Map<Integer, QuizDto.QuizFactorDto> value = redisTemplate.opsForValue().get(memberId);

        value.get(quizId).setIsCorrect(true);
        log.info("updateQuizState : " + quizId + " || " + value.get(quizId).getQuiz());
    }

    public QuizAnswerDto.UpdatedFactors getNewFactor(Integer memberId, Integer quizId, Integer categoryId) { //갱신 목적인가
        //List<QuizWeightFactorDto> newFactors = new ArrayList<>();
        Map<Integer, QuizDto.QuizFactorDto> value = redisTemplate.opsForValue().get(memberId);
        QuizDto.QuizFactorDto preFactors = value.get(quizId);

        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + preFactors.getQuiz() + " | " + preFactors.getHint());

        //QuizAnswerDto.UpdatedFactors newFactors = new QuizAnswerDto.UpdatedFactors();

        boolean isCorrect = preFactors.getIsCorrect();
        boolean usedHint = preFactors.getIsUsedHint();
        Integer point = 0;
        Integer score = 0;

        Double ef = preFactors.getEf();
        Double interval = preFactors.getQuizInterval();

        int quality = 0;

        if (isCorrect) {
            if (!usedHint) {
                quality = 3;
                point += 10;
            }
            else {
                quality = 2;
                point += 3;
            }

            score += 10;
        }

        Double calc = 0.06 + 0.08 * quality + 0.02 * quality * quality;

        Double newEf = ef - calc < 1.3 ? 1.3 : (ef - calc > 2.5 ? 2.5 : ef - calc);
        Double newInterval = interval * newEf;

        return new QuizAnswerDto.UpdatedFactors(memberId, quizId, categoryId, newInterval, newEf, score, point);
    }

    // 가중치 확인하는 함수
    public void checkWeightArray(double[] weights){
        int size = weights.length;
        log.info(Arrays.toString(weights));
    }

    // 소숫점 digit 자리까지 반올림하는 함수
    public double toFixed(double value, int digit){
        return BigDecimal.valueOf(value).setScale(digit, RoundingMode.HALF_UP).doubleValue();
    }

    // 저장 확인 완료
    public void storeQuizToRedis(List<QuizDto.QuizFactorDto> quizInfoList){

        Integer key = 1;
        Map<Integer, QuizDto.QuizFactorDto> map = new HashMap<>();

        for(QuizDto.QuizFactorDto quizInfo : quizInfoList) {
            Integer quizId = quizInfo.getQuizId();

            map.put(quizId, quizInfo);

            //log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> SET INFO" + quizInfo.toString());
        }

        redisTemplate.opsForValue().set(key, map);

        Map<Integer, QuizDto.QuizFactorDto> data = redisTemplate.opsForValue().get(key);
        log.info("퀴즈 1번 데이터 : " + data.get(1).getHint());
    }

    public QuizDto.QuizFactorDto getQuizHint(Integer memberId, Integer quizId){
        Map<Integer, QuizDto.QuizFactorDto> value = redisTemplate.opsForValue().get(memberId);
        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> VALUE : " + value.get(quizId));
        return value.get(quizId);
    }
}
