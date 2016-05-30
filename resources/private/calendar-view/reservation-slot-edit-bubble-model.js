LUPAPISTE.ReservationSlotEditBubbleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.slot = ko.observable();
  self.positionTop = ko.observable();
  self.weekdayCss = ko.observable();

/*  self.startTime = ko.observable();
  self.calendarId = lupapisteApp.services.calendarService.calendarQuery.calendarId;
  self.reservationTypes = lupapisteApp.services.calendarService.calendarQuery.reservationTypes;
  self.selectedReservationTypes = ko.observableArray(); */
  self.bubbleVisible = ko.observable(false);

  self.waiting = params.waiting;
  self.error = ko.observable(false);

  self.okEnabled = self.disposedComputed(function() {
//    var amount = _.toInteger(self.amount());
//    var isValid = amount > 0 && !_.isEmpty(self.selectedReservationTypes());
//    return isValid;
  });

  self.removeEnabled = true;

  self.remove = function() {
  };

  self.send = function() {
/*    if (self.amount() > self.maxAmount()) {
      self.error("calendar.error.cannot-create-overlapping-slots");
      return;
    }
    var slots = _.map(_.range(self.amount()), function(d) {
      var t1 = moment(self.startTime()).add(d, "h");
      var t2 = moment(self.startTime()).add(d+1, "h");
      return {
        start: t1.valueOf(),
        end: t2.valueOf(),
        reservationTypes: self.selectedReservationTypes()
      }
    });
    self.sendEvent("calendarService", "createCalendarSlots", {calendarId: self.calendarId(), slots: slots}); */
    self.bubbleVisible(false);
  };

  self.addEventListener("calendarView", "reservationSlotClicked", function(event) {
    var timestamp = moment(event.slot.startTime);
    self.positionTop((event.hour - params.tableFirstFullHour() + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.bubbleVisible(true);
  });

};