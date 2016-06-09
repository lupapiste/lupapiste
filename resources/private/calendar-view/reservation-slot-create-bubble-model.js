LUPAPISTE.ReservationSlotCreateBubbleModel = function() {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService,
      params = calendarService.params();

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.startTime = ko.observable();
  self.durationHours = params.timeSlotLengthMinutes / 60;
  self.durationMinutes = params.timeSlotLengthMinutes % 60;

  self.positionTop = ko.observable();
  self.weekdayCss = ko.observable();

  self.calendarId = calendarService.calendarQuery.calendarId; // observable
  self.amount = ko.observable();
  self.maxAmount = ko.observable();

  self.reservationTypes = calendarService.calendarQuery.reservationTypes; // observable
  self.selectedReservationTypes = ko.observableArray();

  self.waiting = ko.observable();
  self.error = ko.observable(false);
  self.bubbleVisible = ko.observable(false);

  self.okEnabled = self.disposedComputed(function() {
    var amount = _.toInteger(self.amount());
    var isValid = amount > 0 && !_.isEmpty(self.selectedReservationTypes());
    return !self.error() && isValid;
  });

  self.send = function() {
    if (self.amount() > self.maxAmount()) {
      self.error("calendar.error.cannot-create-overlapping-slots");
      return;
    }
    var len = params.timeSlotLengthMinutes;
    var slots = _.map(_.range(self.amount()), function(d) {
      var t1 = moment(self.startTime()).add(d*len, "minutes");
      var t2 = moment(self.startTime()).add((d+1)*len, "minutes");
      return {
        start: t1.valueOf(),
        end: t2.valueOf(),
        reservationTypes: self.selectedReservationTypes()
      };
    });
    self.sendEvent("calendarService", "createCalendarSlots", {calendarId: self.calendarId(), slots: slots});
    self.bubbleVisible(false);
  };

  self.init = function() {
  };

  function maxFreeTimeAfterGivenTime(weekday, timestamp) {
    var laterSlots = _.filter(weekday.slots, function(slot) { return slot.startTime > timestamp; });
    var nextSlotStartTime = laterSlots[0] ? laterSlots[0].startTime : weekday.endOfDay;
    return moment(nextSlotStartTime).diff(timestamp, "seconds");
  }

  self.addEventListener("calendarView", "timelineSlotClicked", function(event) {
    var weekday = event.weekday;
    var hour = event.hour;
    var minutes = event.minutes;
    var timestamp = moment(weekday.startOfDay).hour(hour).minutes(minutes);

    self.error(false);
    // can not create slots to the past
    if (timestamp.isBefore(moment())) {
      self.error("calendar.error.slot-in-past");
    }

    self.startTime(timestamp);
    self.positionTop((event.hour - params.firstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.selectedReservationTypes([]);
    self.amount(1);
    self.maxAmount(maxFreeTimeAfterGivenTime(weekday, timestamp.valueOf()) / 3600);
    self.bubbleVisible(true);
  });

};