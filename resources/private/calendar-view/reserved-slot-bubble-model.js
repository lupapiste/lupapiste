LUPAPISTE.ReservedSlotBubbleModel = function(params) {
  "use strict";
  var self = this,
      config = LUPAPISTE.config.calendars;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.currentApplication = params.applicationModel;
  self.relatedToOtherApplication = ko.observable(false);

  self.reservation = ko.observable();
  self.calendarId = ko.observable(params.calendarId);
  self.endHour = ko.observable();
  
  self.weekdayCss = ko.observable();
  self.positionTop = ko.observable();
  self.waiting = ko.observable();
  self.error = ko.observable(false);
  self.bubbleVisible = ko.observable(false);

  self.removeVisible = self.disposedComputed(function() {
    return lupapisteApp.models.currentUser.isAuthority() && !self.relatedToOtherApplication();
  });

  self.cancelReservation = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("reservation.confirm-cancel"), {title: loc("yes"), fn: function() {
      self.sendEvent("calendarService", "cancelReservation",
        { clientId: _.get(params.client(), "id"),
          authorityId: _.get(params.authority(), "id"),
          reservationTypeId: _.get(params.reservationType(), "id"),
          applicationId: lupapisteApp.models.application.id(),
          reservationId: self.reservation().id,
          weekObservable: params.weekdays });
      self.bubbleVisible(false);
    }});
  };
  
  self.addEventListener("calendarView", "bookedSlotClicked", function(event) {
    self.reservation(event.slot.reservation);
    var currentApplicationId = self.currentApplication ? self.currentApplication().id : "";
    var reservationApplicationId = self.reservation() ? self.reservation().applicationId : "";
    self.relatedToOtherApplication(!_.isEmpty(currentApplicationId) &&
        currentApplicationId !== reservationApplicationId);

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