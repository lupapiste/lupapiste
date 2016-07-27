LUPAPISTE.ReservationSlotReserveBubbleModel = function(params) {
  "use strict";
  var self = this,
    calendarService = lupapisteApp.services.calendarService,
    config = calendarService.params();

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.client = params.client;
  self.authority = params.authority;
  self.reservationTypeId = params.reservationTypeId;
  self.reservationTypes = params.reservationTypes;

  self.slot = ko.observable();
  self.location = ko.observable();
  self.comment = ko.observable();
  self.participants = ko.observableArray([]);
  self.reservationType = ko.observable();
  self.endHour = ko.observable();

  self.weekdayCss = ko.observable();
  self.positionTop = ko.observable();
  self.waiting = ko.observable();
  self.error = ko.observable(false);
  self.bubbleVisible = ko.observable(false);

  self.okEnabled = self.disposedComputed(function() {
    return true;
  });

  self.send = function() {
    self.sendEvent("calendarService", "reserveCalendarSlot",
      { clientId: _.get(self.client(), "id"),
        authorityId: _.get(self.authority(), "id"),
        slot: self.slot,
        reservationTypeId: self.reservationTypeId,
        comment: self.comment,
        weekObservable: params.weekdays});
    self.bubbleVisible(false);
  };

  self.init = function() {
  };

  self.addEventListener("calendarView", "availableSlotClicked", function(event) {

    self.slot(event.slot);
    self.reservationType(_.find(self.reservationTypes(), function(reservationType) { return reservationType.id === self.reservationTypeId(); }));

    var party = self.client();
    self.participants([lupapisteApp.models.currentUser.displayName(), party.firstName + " " + party.lastName]);

    //self.location(params.defaultLocation());

    var hour = moment(event.slot.startTime).hour();
    var minutes = moment(event.slot.startTime).minute();
    var timestamp = moment(event.weekday.startOfDay).hour(hour).minutes(minutes);
    self.endHour(moment(event.slot.endTime).format("HH:mm"));

    self.error(false);

    self.positionTop((hour - config.firstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.bubbleVisible(true);
  });

};