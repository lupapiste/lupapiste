LUPAPISTE.AuthAdminCalendarsModel = function () {
  "use strict";

  var self = this;

  function NewReservationSlotModel() {
    var self = this;

    self.startTime = ko.observable();
    self.reservationTypes = ko.observableArray();
    self.commandName = ko.observable();
    self.command = null;

    self.execute = function() { self.command(self.reservationTypes()); };

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.startTime(util.getIn(params, ["source", "startTime"], ""));
    };
  }

  self.newReservationSlotModel = new NewReservationSlotModel();
  hub.subscribe("calendarView::timelineSlotClicked", function(event) {
    var weekday = event.weekday;
    var hour = event.hour;
    var minutes = event.minutes;
    var startTime = moment(weekday.startOfDay).hour(hour).minutes(minutes);
    self.newReservationSlotModel.init({
      source: { startTime: startTime },
      commandName: "create",
      command: function(reservationTypes) {
        var slots = [{start: startTime.valueOf(), end: moment(startTime).add(30, "m").valueOf(), reservationTypes: reservationTypes}];
        hub.send("calendarService::createCalendarSlots", {calendarId: weekday.calendarId, slots: slots, modalClose: true});
      }
    });
    self.openNewReservationSlotDialog();
  });

  self.openNewReservationSlotDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-new-slot");
  };

  self.items = ko.observableArray();
  self.initialized = false;

  function setEnabled(user, value) {
    if (self.initialized) {
      ajax.command("set-calendar-enabled-for-authority", {userId: user.id, enabled: value})
        .success(function(response) {
          util.showSavedIndicator(response);
          user.calendarId(response.calendarId);
        })
        .call();
    }
  }

  self.init = function(data) {
    var users = _.map(data.users,
      function(user) {
        var calendarEnabledObservable = ko.observable(_.has(user, "calendarId"));
        var calendarIdForLink = ko.observable(user.calendarId || "");
        user = _.extend(user, { calendarEnabled: calendarEnabledObservable,
                                    calendarId: calendarIdForLink });
        user.calendarEnabled.subscribe(_.partial(setEnabled, user));
        return user;
      });
    self.items(users || []);
    self.initialized = true;
  };

  self.load = function() {
    ajax.query("calendars-for-authority-admin")
      .success(function(d) {
        self.init(d);
      })
      .call();
  };
};