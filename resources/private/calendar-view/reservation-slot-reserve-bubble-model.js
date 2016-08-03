LUPAPISTE.ReservationSlotReserveBubbleModel = function(params) {
  "use strict";
  var self = this,
    calendarService = lupapisteApp.services.calendarService,
    config = calendarService.params();

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.client = params.client;
  self.authority = params.authority;
  self.reservationTypeId = params.reservationTypeId;
  self.reservationTypes = params.reservationTypes;

  self.slot = ko.observable();
  self.location = ko.observable();
  self.comment = ko.observable("");
  self.participants = ko.observableArray([]);
  self.reservationType = ko.observable();
  self.endHour = ko.observable();

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
      { clientId: _.get(self.client(), "id"),
        authorityId: _.get(self.authority(), "id"),
        slot: self.slot,
        reservationTypeId: self.reservationTypeId,
        comment: self.comment,
        location: self.location,
        applicationId: lupapisteApp.models.application.id,
        weekObservable: params.weekdays});
    self.bubbleVisible(false);
  };

  self.init = _.noop();

  self.authorityDisplayText = function() {
    var authority = self.authority();
    if (authority) {
      var text = authority.firstName + " " + authority.lastName;
      if (!_.isEmpty(lupapisteApp.models.application.organizationName())) {
        text += ", ";
        text += lupapisteApp.models.application.organizationName();
      }
      return text;
    }
  };

  self.clientDisplayText = function() {
    var client = self.client();
    if (client) {
      var text = client.firstName + " " + client.lastName;
      if (!_.isEmpty(client.partyType)) {
        _.forEach(client.partyType, function (t) { text += ", " + loc("schemas."+t); });
      }
      return text;
    }
  };

  self.addEventListener("calendarView", "availableSlotClicked", function(event) {

    self.slot(event.slot);
    self.reservationType(_.find(self.reservationTypes(), function(reservationType) { return reservationType.id === self.reservationTypeId(); }));

    self.location(params.defaultLocation());

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