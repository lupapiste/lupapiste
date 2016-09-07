LUPAPISTE.ReservationSlotEditBubbleModel = function(params) {
  "use strict";
  var self = this,
      config = LUPAPISTE.config.calendars;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.slotId = ko.observable();
  self.startTime = ko.observable();
  self.durationHours = ko.observable();
  self.durationMinutes = ko.observable();

  self.positionTop = ko.observable();
  self.weekdayCss = ko.observable();

  self.reservationTypes = params.reservationTypes; // observableArray from parent
  self.selectedReservationTypes = ko.observableArray();

  self.waiting = ko.observable();
  self.error = ko.observable(false);
  self.bubbleVisible = ko.observable(false);

  self.okEnabled = self.disposedComputed(function() {
    return !_.isEmpty(self.selectedReservationTypes());
  });

  self.removeEnabled = true;

  self.doRemove = function() {
    self.sendEvent("calendarService", "deleteCalendarSlot", {id: self.slotId(), calendarId: _.parseInt(params.calendarId()),
                                                             weekObservable: params.weekdays});
    self.bubbleVisible(false);
  };

  self.remove = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("calendar.slot.confirmdelete"), {title: loc("yes"), fn: self.doRemove});
  };

  self.send = function() {
    self.sendEvent("calendarService", "updateCalendarSlot", {id: self.slotId(), calendarId: _.parseInt(params.calendarId()),
                                                             reservationTypes: self.selectedReservationTypes(),
                                                             weekObservable: params.weekdays});
    self.bubbleVisible(false);
  };

  self.addEventListener("calendarView", "calendarSlotClicked", function(event) {
    var timestamp = moment(event.slot.startTime);
    var durationMoment = moment.duration(event.slot.duration);
    self.slotId(event.slot.id);
    self.startTime(timestamp);
    self.durationHours(durationMoment.hours());
    self.durationMinutes(durationMoment.minutes());
    self.selectedReservationTypes(_.map(event.slot.reservationTypes, function(d) { return d.id; }));
    self.positionTop((timestamp.hour() - config.firstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.bubbleVisible(true);
  });

};