LUPAPISTE.ReservationSlotCreateBubbleModel = function(params) {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService,
      config = calendarService.params();

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.startTime = ko.observable();
  self.durationHours = _.parseInt(config.timeSlotLengthMinutes / 60);
  self.durationMinutes = _.parseInt(config.timeSlotLengthMinutes % 60);

  self.positionTop = ko.observable();
  self.weekdayCss = ko.observable();

  self.amount = ko.observable();
  self.maxAmountUntilNextSlot = ko.observable();
  self.maxAmountUntilEndOfDay = ko.observable();

  self.reservationTypes = params.reservationTypes; // observable
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
    var len = config.timeSlotLengthMinutes;
    var slots = _.map(_.range(self.amount()), function(d) {
      var t1 = moment(self.startTime()).add(d*len, "minutes");
      var t2 = moment(self.startTime()).add((d+1)*len, "minutes");
      return {
        start: t1.valueOf(),
        end: t2.valueOf(),
        reservationTypes: self.selectedReservationTypes()
      };
    });
    self.sendEvent("calendarService", "createCalendarSlots",
        {calendarId: _.parseInt(params.calendarId()), slots: slots, weekObservable: params.weekdays});
    self.bubbleVisible(false);
  };

  self.disposedComputed(function() {
    if (self.amount() > self.maxAmountUntilEndOfDay()) {
      self.error("calendar.error.cannot-create-slots-outside-calendar-day");
    } else if (self.amount() > self.maxAmountUntilNextSlot()) {
      self.error("calendar.error.cannot-create-overlapping-slots");
    } else {
      self.error(false);
    }
  });

  self.amountMinus = function() {
    var amount = self.amount();
    if (_.isInteger(amount) && amount > 1) {
      self.amount(amount - 1);
    }
  };

  self.amountPlus = function() {
    self.amount(self.amount() + 1);
  };

  self.init = function() {
  };

  function calculateFreeTimeAfterGivenTime(weekday, timestamp) {
    var laterSlots = _.filter(weekday.slots, function(slot) { return slot.startTime > timestamp; });
    var nextSlotStartTime = laterSlots[0] ? laterSlots[0].startTime : weekday.endOfDay;
    self.maxAmountUntilNextSlot(moment(nextSlotStartTime).diff(timestamp, "seconds") / (config.timeSlotLengthMinutes * 60));
    self.maxAmountUntilEndOfDay(moment(weekday.endOfDay).diff(timestamp, "seconds") / (config.timeSlotLengthMinutes * 60));
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
    self.positionTop((event.hour - config.firstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.selectedReservationTypes([]);
    self.amount(1);
    calculateFreeTimeAfterGivenTime(weekday, timestamp.valueOf());
    self.bubbleVisible(true);
  });

};