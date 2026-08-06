package com.otboo.domain.weather.dto;

import java.util.List;

public record WeatherAPILocation (

    //위도
    double latitude,

    //경도
    double longitude,

    // 기상청 행정구역 좌표
    int x,
    int y,

    // 행정 구역 이름
    // 배열(String[])이 아니라 List를 쓰는 이유: record는 컴포넌트별로 equals/hashCode/toString을
    // 자동 생성하는데, 배열은 그 안에서 내용이 아니라 참조 동일성으로만 비교돼서 논리적으로 같은
    // 값을 가진 두 인스턴스도 equals()가 false가 나오는 함정이 있음. JSON 직렬화 결과는 List/배열 둘 다
    // 동일한 배열 형태(["a","b","c"])라 API 응답 형식엔 영향 없음.
    List<String> locationNames

){

}
