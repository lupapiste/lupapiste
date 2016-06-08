LUPAPISTE.ReservationSlotEditBubbleModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.slotId = ko.observable();
  self.startTime = ko.observable();
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
    self.slotId(event.slot.id);
    self.startTime(timestamp);
    self.selectedReservationTypes(_.map(event.slot.reservationTypes, function(d) { return d.id; }));
    self.positionTop((timestamp.hour() - params.tableFirstFullHour + 1) * 60 + "px");
    self.weekdayCss("weekday-" + timestamp.isoWeekday());
    self.bubbleVisible(true);
  });

};