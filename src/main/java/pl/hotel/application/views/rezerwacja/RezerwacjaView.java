package pl.hotel.application.views.rezerwacja;

import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import ch.qos.logback.core.Layout;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;

import pl.hotel.application.data.RoomType;
import pl.hotel.application.data.entity.Reservation;
import pl.hotel.application.data.entity.Room;
import pl.hotel.application.data.entity.User;
import pl.hotel.application.data.service.ReservationService;
import pl.hotel.application.data.service.RoomService;
import pl.hotel.application.data.service.UserService;
import pl.hotel.application.views.MainLayout;

@PageTitle("Rezerwacja")
@Route(value = "rezerwacja", layout = MainLayout.class)
//@PermitAll
@AnonymousAllowed
public class RezerwacjaView extends VerticalLayout {

	private final ReservationService reservationService;
	private final UserService userService;
	private final RoomService roomService;
	private TextField username = new TextField("Nazwa użytkownika:");
	private TextField name = new TextField("Imię i nazwisko:");
	private PasswordField password = new PasswordField("Hasło:");
	private DatePicker dateFrom = new DatePicker("Od:");
	private DatePicker dateTo = new DatePicker("Do:");
	private Button cancel = new Button("Anuluj");
	private Button cancelReservations = new Button("Anuluj rezerwacje");
	private Button reserve = new Button("Rezerwuj");
	private Button logIn = new Button("Zaloguj się");
	private Button logOut = new Button("Wyloguj się");
	private Button addToCart = new Button("Dodaj");
	private Button summary = new Button("Podsumowanie");

	private Checkbox withBalcony = new Checkbox("Z balkonem?");
	private H3 info = new H3("Zaloguj się, aby dokonać rezerwacji:");
	private ComboBox<String> roomTypeCombobox = new ComboBox<>("Pokój:");
	private List<Reservation> reservationsInCart = new ArrayList<>();
	private Grid<Reservation> reservationInCartGrid = new Grid<>(Reservation.class, false);

	private H3 totalFeeInfo = new H3();
	private int resIdIterator = 1;
	private double totalFee = 0;
	private boolean wasPreviouslyCanceled = false;
	public RezerwacjaView(ReservationService reservationService, UserService userService, RoomService roomService) {
		this.reservationService = reservationService;
		this.userService = userService;
		this.roomService = roomService;
		setSpacing(false);
		setSizeFull();
		
		reservationInCartGrid.addColumns("reservationNumber", "from", "to", "description", "totalFee");
		List<String> typeList = new ArrayList<>();
		typeList.add("Mały");
		typeList.add("Średni");
		typeList.add("Duży");
		typeList.add("Apartament");
		roomTypeCombobox.setItems(typeList);
		add(info, username, password, logIn);
		VerticalLayout summaryLayout = new VerticalLayout();
		summaryLayout.add(reservationInCartGrid, totalFeeInfo, reserve, cancelReservations);
		summaryLayout.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
		summary.addClickListener(e -> {
			if (reservationsInCart.size() > 0) {
				reservationInCartGrid.setItems(reservationsInCart);

				info.setText("Podsumowanie:");
				totalFeeInfo.setText("Do zapłaty: " + totalFee);
				remove(dateFrom, dateTo, name, logOut, cancel, addToCart, summary, roomTypeCombobox, withBalcony);
				add(summaryLayout);
			} else {
				Notification.show("Najpierw dodaj rezerwację!");
			}
		});
		// anulowanie wszystkich rezerwacji w ekranie rezerwowania
		cancel.addClickListener(e -> {

			dateFrom.setValue(LocalDate.now());
			dateTo.setValue(LocalDate.now());
			reservationsInCart.forEach(res -> {
				roomService.deleteReservationFromRoom(res.getRoom(), res);
			});
			reservationsInCart.forEach(res -> {
				reservationService.removeReservation(res);
			});
			resIdIterator += reservationsInCart.size();
			wasPreviouslyCanceled = true;
			reservationsInCart.clear();
			totalFee = 0;
			Notification.show("Anulowano wszystkie rezerwacje!");
		});
		// dodanie rezerwacji do koszyka
		addToCart.addClickListener(e -> {
			if (roomTypeCombobox.getValue() != null) {
				
				if ((dateFrom.getValue().atStartOfDay().isEqual(LocalDate.now().atStartOfDay())
						|| dateFrom.getValue().atStartOfDay().isAfter(LocalDate.now().atStartOfDay()))
						&& dateTo.getValue().atStartOfDay().isAfter(LocalDate.now().atStartOfDay())
						&& dateTo.getValue().atStartOfDay().isAfter(dateFrom.getValue().atStartOfDay())) {
					List<Reservation> reservations = reservationService.getReservations();
					Room maybeRoom = new Room();
					RoomType roomType = getRoomType(roomTypeCombobox.getValue());
					if (reservations.size() == 0) {
						maybeRoom = roomService.getRoomByTypeAndBalcony(roomType, withBalcony.getValue()).stream()
								.findAny().orElse(null);
					} else {						
						maybeRoom = findARoom(roomService, withBalcony, dateFrom, dateTo, roomType);
					}
					// jeżeli znaleziono pokój
					if (maybeRoom != null) {
						if(wasPreviouslyCanceled) {
							wasPreviouslyCanceled = false;
						}else {
							resIdIterator = 1;
						}
						Reservation res = new Reservation();
						User u = userService.getUserByNick(username.getValue());
						res.setEnded(false);
						Duration diff = Duration.between(dateFrom.getValue().atStartOfDay(),
								dateTo.getValue().atStartOfDay());
						Long diffDays = diff.toDays();
						res.setFee(maybeRoom.getPrice());
						res.setFrom(dateFrom.getValue());
						res.setTo(dateTo.getValue());
						res.setTotalFee(maybeRoom.getPrice() * diffDays);
						res.setDescription(roomTypeCombobox.getValue());
						if (dateFrom.getValue().atStartOfDay().isEqual(LocalDate.now().atStartOfDay())) {
							res.setStarted(true);
						} else {
							res.setStarted(false);
						}
						totalFee += maybeRoom.getPrice() * diffDays;
						List<Reservation> reservs = reservationService.getReservations();
						Reservation ress = reservs.stream().max(Comparator.comparing(Reservation::getReservationNumber))
								.orElse(null);
						if (ress != null) {
							res.setReservationNumber(ress.getReservationNumber() + resIdIterator);
						} else {
							res.setReservationNumber(0);
						}
						res.setRoom(maybeRoom);
						res.setRoomUser(u);
						roomService.addReservationToRoom(maybeRoom, res);
						reservationsInCart.add(res);
						Notification.show("Dodano rezerwację do koszyka. Termin: " + dateFrom.getValue().toString());
						maybeRoom = null;
					} else {
						Notification.show("Brak wolnych pokojów tego typu!");
					}

				} else {
					Notification.show("Wybierz poprawny termin rozpoczęcia i zakończenia rezerwacji!");
				}
			} else {
				Notification.show("Wybierz pokój!");
			}
		});
		// logowanie
		logIn.addClickListener(e -> {
			if (username.getValue() != "" && password.getValue() != "") {
				User user = userService.getUserByNick(username.getValue());
				if (!(user == null)) {
					if (user.getPassword().equals(password.getValue())) {

						name.setValue(user.getName());
						name.setEnabled(false);
						remove(logIn, password, username);
						add(name, cancel, dateFrom, dateTo, roomTypeCombobox, withBalcony, addToCart, summary, cancel,
								logOut);
						info.setText("Wypelnij ponizsze pola, aby dokonac rezerwacji: ");
					} else {
						Notification.show("Niepoprawne hasło!");
					}
				} else {
					Notification.show("Nie znaleziono użytkownika!");
				}
			} else {
				Notification.show("Wypełnij oba pola!");
			}
		});
		// wylogowywanie
		logOut.addClickListener(e -> {
			info.setText("Zaloguj się, aby dokonać rezerwacji:");
			remove(dateFrom, dateTo, name, logOut, cancel, addToCart, summary, roomTypeCombobox, withBalcony);
			add(username, password, logIn);
			dateFrom.clear();
			dateTo.clear();
			roomTypeCombobox.setValue(null);
			withBalcony.setValue(false);
			username.setValue("");
			password.setValue("");
			name.clear();
			totalFee = 0;
		});
		// anulowanie rezerwacji (w ekranie podsumowania)
		cancelReservations.addClickListener(e -> {
			dateFrom.setValue(LocalDate.now());
			dateTo.setValue(LocalDate.now());
			reservationsInCart.forEach(res -> {
				roomService.deleteReservationFromRoom(res.getRoom(), res);
			});
			reservationsInCart.forEach(res -> {
				reservationService.removeReservation(res);
			});
			resIdIterator += reservationsInCart.size();
			wasPreviouslyCanceled = true;
			reservationsInCart.clear();
			totalFee = 0;
			remove(summaryLayout);
			info.setText("Wypelnij ponizsze pola, aby dokonac rezerwacji: ");
			add(name, cancel, dateFrom, dateTo, roomTypeCombobox, withBalcony, addToCart, summary, cancel, logOut);
			Notification.show("Anulowano wszystkie rezerwacje!");
		});
		// wybór pokoju
		roomTypeCombobox.addValueChangeListener(e -> {
			if (e.getOldValue() != null && e.getOldValue().equals("Apartament")
					&& !(e.getValue().equals("Apartament"))) {
				withBalcony.setValue(false);
			}
			if (e.getValue().equals("Apartament")) {
				withBalcony.setValue(true);
				withBalcony.setEnabled(false);
			} else {
				withBalcony.setEnabled(true);
			}
		});
		//zatwierdzenie rezerwacji
		reserve.addClickListener(e -> {
			Notification.show("Dokonano " + reservationsInCart.size() + " rezerwacji!");
			remove(summaryLayout);
			add(name, cancel, dateFrom, dateTo, roomTypeCombobox, withBalcony, addToCart, summary, cancel, logOut);
			reservationsInCart.clear();
			dateFrom.setValue(LocalDate.now());
			dateTo.setValue(LocalDate.now());
			withBalcony.setValue(false);

			totalFee = 0;
		});
		setDefaultHorizontalComponentAlignment(Alignment.CENTER);
		getStyle().set("text-align", "center");
	}
	// funkcja od zwracania typu pokoju
	private RoomType getRoomType(String type) {
		switch (type) {
		case "Mały":
			return RoomType.MALY;
		case "Średni":
			return RoomType.SREDNI;
		case "Duży":
			return RoomType.DUZY;
		case "Apartament":
			return RoomType.APARTAMENT;
		}
		return null;
	}
	// algorytm szukania wolnego pokoju
	private Room findARoom(RoomService roomService, Checkbox balcony, DatePicker dateFrom, DatePicker dateTo,
			RoomType roomType) {
		List<Room> rooms = roomService.getRooms().stream()
				.filter(r -> r.getRoomType().equals(roomType) && r.isBalcony() == balcony.getValue())
				.collect(Collectors.toList());
		for (Room room : rooms) {
			boolean roomFound = true;
			List<Reservation> reservations = new ArrayList<>(room.getReservations());
			if (reservations.size() == 0) {
				return room;
			}
			for (Reservation res : reservations) {
				if ((res.getFrom().isBefore(dateFrom.getValue()) && res.getTo().isAfter(dateTo.getValue()))
						|| (res.getFrom().isAfter(dateFrom.getValue()) && res.getFrom().isBefore(dateTo.getValue()))
						|| (res.getTo().isAfter(dateFrom.getValue()) && res.getTo().isBefore(dateTo.getValue()))
						|| (res.getFrom().atStartOfDay().isEqual(dateFrom.getValue().atStartOfDay()))
						|| (res.getTo().atStartOfDay().isEqual(dateTo.getValue().atStartOfDay()))) {
					// pokoj nie jest wolny
					roomFound = false;
					break;
				}
			}
			if (roomFound) {
				return room;
			}
		}
		return null; //brak pokoju
	}
}
