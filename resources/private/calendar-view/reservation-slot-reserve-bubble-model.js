LUPAPISTE.ReservationSlotReserveBubbleModel = function(params) {
  "use strict";
  var self = this,
    config = LUPAPISTE.config.calendars;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.client = params.client;
  self.clientId = ko.observable();
  self.applicationId = ko.observable();
  self.authority = params.authority;
  self.reservationType = params.reservationType;
  self.applicationModel = params.applicationModel;

  self.slot = ko.observable();
  self.location = ko.observable("");
  self.comment = ko.observable("");
  self.participants = ko.observableArray([]);
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
      { clientId: self.clientId(),
        authorityId: _.get(ko.unwrap(self.authority), "id"),
        slot: self.slot,
        reservationTypeId: _.get(ko.unwrap(self.reservationType), "id"),
        comment: self.comment,
        location: self.location,
        applicationId: self.applicationId(),
        weekObservable: params.weekdays });
    params.authority(null);
    params.reservationType(null);
    self.bubbleVisible(false);
  };

  self.init = _.noop();

  self.authorityDisplayText = function() {
    var authority = self.authority();
    var model = ko.unwrap(self.applicationModel);
    var orgName = ko.unwrap(model.organizationName);
    if (authority) {
      var text = util.partyFullName(authority);
      if (!_.isEmpty(orgName)) {
        text += ", ";
        text += orgName;
      }
      return text;
    }
  };

  self.clientDisplayText = function() {
    var client = self.client();
    if (client) {
      var text = util.partyFullName(client);
      if (!_.isEmpty(client.partyType)) {
        _.forEach(client.partyType, function (t) { text += ", " + loc("schemas."+t); });
      }
      return text;
    }
  };

  self.addEventListener("calendarView", "availableSlotClicked", function(event) {


    self.slot(event.slot);
    self.location(params.defaultLocation());

    var client = params.client();
    if (client) {
      self.clientId(ko.unwrap(client.id));
    }
    var appModel = ko.unwrap(self.applicationModel);
    if (appModel) {
      self.applicationId(ko.unwrap(appModel.id));
    }


    self.comment("");

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