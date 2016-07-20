LUPAPISTE.ReservationSlotReserveBubbleModel = function(params) {
  "use strict";
  var self = this,
    calendarService = lupapisteApp.services.calendarService,
    config = calendarService.params();

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.clientId = ko.observable();
  self.reservationTypeId = ko.observable();
  self.slotId = ko.observable();
  self.comment = ko.observable();

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
      { clientId: self.clientId(),
        slotId: self.slotId(),
        reservationTypeId: self.reservationTypeId(),
        comment: self.comment(),
        weekObservable: params.weekdays});
    self.bubbleVisible(false);
  };

  self.init = function() {
  };

  self.addEventListener("calendarView", "availableSlotClicked", function(event) {

    self.clientId = event.clientId;
    self.reservationTypeId = event.reservationTypeId;
    self.slotId(event.slot.id);

    var hour = moment(event.slot.startTime).hour();
    var minutes = moment(event.slot.startTime).minute();
    var timestamp = moment(event.weekday.startOfDay).hour(hour).minutes(minutes);

    self.error(false);

    self.positionTop((hour - config.firstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.bubbleVisible(true);
  });

};