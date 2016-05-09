LUPAPISTE.AuthAdminCalendarsModel = function () {
  "use strict";

  var self = this;

  function NewReservationSlotModel() {
    var self = this;

    self.startTime = ko.observable();
    self.services = ko.observableArray();
    self.commandName = ko.observable();
    self.command = null;

    self.execute = function() { self.command(self.services()); };

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.startTime(util.getIn(params, ["source", "startTime"], ""));
    };
  };

  self.newReservationSlotModel = new NewReservationSlotModel();
  hub.subscribe("calendarView::timelineSlotClicked", function(event) {
    var weekday = event.weekday;
    var hour = event.hour;
    var minutes = event.minutes;
    var startTime = moment(weekday.startOfDay).hour(hour).minutes(minutes);
    self.newReservationSlotModel.init({
      source: { startTime: startTime },
      commandName: 'create',
      command: function(services) {
        var slots = [{start: startTime.valueOf(), end: moment(startTime).add(30, 'm').valueOf(), services: services}];
        hub.send("calendarService::createCalendarSlots", {calendarId: weekday.calendarId, slots: slots, modalClose: true});
      }
    });
    self.openNewReservationSlotDialog();
  });

  hub.subscribe("calendarView::reservationSlotClicked", function(event) {
    console.log(event);
  });

  self.openNewReservationSlotDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-new-slot");
  };

  self.items = ko.observableArray();
  self.initialized = false;

  self.init = function(data) {
    var users = _.map(data.users,
      function(user) {
        var calendarEnabledObservable = ko.observable(_.has(user, 'calendarId'));
        var calendarIdForLink = ko.observable(user.calendarId || '');
        ko.computed(function() {
          var enabled = calendarEnabledObservable();
          if (self.initialized) {
            console.log("inside", user);
            ajax.command("set-calendar-enabled-for-authority", {userId: user.id, enabled: enabled})
              .success(function(response) {
                  util.showSavedIndicator(response);
                  calendarIdForLink(response.calendarId);
                })
              .call();
          }
        });
        return _.extend(user, { calendarEnabled: calendarEnabledObservable,
                                calendarId: calendarIdForLink, });
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