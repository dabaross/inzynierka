package dev.damian.wifi_ai_analyzer.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/*
Co oznacza poniższy zapis - interfejs RawFrameEvenetReposityry dziedziczy po wbudowanym
w springa interfejsie JpaRepository. Dzięki temu dostajemy metody do obsługi bazy danych.

RawFrameEventEntity - wskazuje na jakiej encji (klasie - tabeli w bazie) to repozytrium ma operować.
Long - typ danych klcz głównego.

Repozytorium to taka warstwa między bazą danych a resztą aplikacji. To interfejs,
który dziedziczy po JpaRepository. Dzięki temu dalej w kodzie możemy korzystać
z prostych metod do obsługi bazy dancyh bez pisania SQL. Robimy to poprzez
wstrzykiwanie tego repozytorium do serwisów lub kontrolerów i wywoływanie metod.

Wstrzykiwanie polega na tym, że spring sam tworzy instancje (czyli obiekty) tego
repozytorium i wstrzykuje je do inncch klas. Nie musimy za każdym razem pamietać o stowrzniu
nowego obiektu, spring robi to za nas.

Serwis to klasa, która zawiera logikę biznesową aplikacji.
Kontroler to klasa, która obsługuje żądania HTTP i zwraca odpowiedzi.
 */
public interface RawFrameEventRepository extends JpaRepository<RawFrameEventEntity, Long> {
}


