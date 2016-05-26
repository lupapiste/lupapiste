LUPAPISTE.ReservationSlotCreateBubbleModel = function( params ) {
  "use strict";
  var self = this;

  self.startTime = ko.observable();
  self.reservationTypes = lupapisteApp.services.calendarService.calendarQuery.reservationTypes;
  self.selectedReservationTypes = ko.observableArray();
  self.bubbleVisible = ko.observable(false);

  self.waiting = params.waiting;
  self.error = params.error;

  self.send = function() {};

  self.init = function() {
    self.error( false );
  };

  var _timelineSlotClicked = hub.subscribe("calendarView::timelineSlotClicked", function(event) {
    var weekday = event.weekday;
    var hour = event.hour;
    var minutes = event.minutes;
    self.startTime(moment(weekday.startOfDay).hour(hour).minutes(minutes));
    self.selectedReservationTypes([]);
/*    self.newReservationSlotModel.init({
      source: { startTime: startTime },
      commandName: 'create',
      command: function(reservationTypes) {
        var slots = [{start: startTime.valueOf(), end: moment(startTime).add(1, 'h').valueOf(), reservationTypes: reservationTypes}];
        hub.send("calendarService::createCalendarSlots", {calendarId: weekday.calendarId, slots: slots, modalClose: true});
      }
    }); */
    self.bubbleVisible(true);
  });

  self.dispose = function() {
    hub.unsubscribe(_timelineSlotClicked);
  };
};