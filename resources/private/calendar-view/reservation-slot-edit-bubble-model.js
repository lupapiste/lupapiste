LUPAPISTE.ReservationSlotEditBubbleModel = function( params ) {
  "use strict";
  var self = this,
      calendarService = lupapisteApp.services.calendarService;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.slotId = ko.observable();
  self.startTime = ko.observable();
  self.durationHours = ko.observable();
  self.durationMinutes = ko.observable();

  self.positionTop = ko.observable();
  self.weekdayCss = ko.observable();

  self.reservationTypes = lupapisteApp.services.calendarService.calendarQuery.reservationTypes;
  self.selectedReservationTypes = ko.observableArray();
  self.bubbleVisible = ko.observable(false);

  self.waiting = params.waiting;
  self.error = ko.observable(false);

  self.okEnabled = self.disposedComputed(function() {
    return !_.isEmpty(self.selectedReservationTypes());
  });

  self.removeEnabled = true;

  self.doRemove = function() {
    self.sendEvent("calendarService", "deleteCalendarSlot", {id: self.slotId()});
    self.bubbleVisible(false);
  };

  self.remove = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("calendar.slot.confirmdelete"), {title: loc("yes"), fn: self.doRemove});
  };

  self.send = function() {
    self.sendEvent("calendarService", "updateCalendarSlot", {id: self.slotId(), reservationTypes: self.selectedReservationTypes()});
    self.bubbleVisible(false);
  };

  self.addEventListener("calendarView", "calendarSlotClicked", function(event) {
    var timestamp = moment(event.slot.startTime);
    var durationHours = moment.duration(event.slot.duration).hours();
    var durationMinutes = moment.duration(event.slot.duration).minutes();
    console.log(event.slot);
    self.slotId(event.slot.id);
    self.startTime(timestamp);
    self.durationHours(durationHours);
    self.durationMinutes(durationMinutes);
    self.selectedReservationTypes(_.map(event.slot.reservationTypes, function(d) { return d.id; }));
    self.positionTop((timestamp.hour() - params.tableFirstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.bubbleVisible(true);
  });

};