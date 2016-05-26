LUPAPISTE.ReservationSlotCreateBubbleModel = function( params ) {
  "use strict";
  var self = this;

  self.startTime = ko.observable();
  self.calendarId = lupapisteApp.services.calendarService.calendarQuery.calendarId;
  self.reservationTypes = lupapisteApp.services.calendarService.calendarQuery.reservationTypes;
  self.selectedReservationTypes = ko.observableArray();
  self.bubbleVisible = ko.observable(false);

  self.waiting = params.waiting;
  self.error = params.error;

  self.send = function() {
    var slots = [{start: self.startTime().valueOf(), end: moment(self.startTime()).add(1, 'h').valueOf(), reservationTypes: self.selectedReservationTypes()}];
    hub.send("calendarService::createCalendarSlots", {calendarId: self.calendarId(), slots: slots});
    self.bubbleVisible(false);
  };

  self.init = function() {
    self.error( false );
  };

  var _timelineSlotClicked = hub.subscribe("calendarView::timelineSlotClicked", function(event) {
    var weekday = event.weekday;
    var hour = event.hour;
    var minutes = event.minutes;
    self.startTime(moment(weekday.startOfDay).hour(hour).minutes(minutes));
    self.selectedReservationTypes([]);
    self.bubbleVisible(true);
  });

  self.dispose = function() {
    hub.unsubscribe(_timelineSlotClicked);
  };
};