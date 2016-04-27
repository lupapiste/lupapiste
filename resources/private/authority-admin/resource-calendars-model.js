LUPAPISTE.ResourceCalendarsModel = function () {
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

  function EditCalendarModel() {
    var self = this;

    self.calendarId = ko.observable();
    self.name = ko.observable();
    self.organization = ko.observable();

    self.commandName = ko.observable();
    self.command = null;

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.calendarId(util.getIn(params, ["source", "id"], -1));
      self.name(util.getIn(params, ["source", "name"], ""));
      self.organization(util.getIn(params, ["source", "organization"], ""));
    };

    self.execute = function() {
      self.command(self.calendarId(), self.name(), self.organization());
    };

    self.ok = ko.computed(function() {
      return !_.isBlank(self.name()) && !_.isBlank(self.organization());
    });
  }
  self.editCalendarModel = new EditCalendarModel();

  self.items = ko.observableArray();

  self.editCalendar = function() {
    self.editCalendarModel.init({
      source: this,
      commandName: "edit",
      command: function(calendarId, name, organization) {
        ajax
          .command("update-calendar", {calendarId: calendarId, name: name, organization: organization})
          .success(function() {
            self.load();
            LUPAPISTE.ModalDialog.close();
          })
          .call();

        LUPAPISTE.ModalDialog.close();
      }
    });
    self.openCalendarEditDialog();
  };

  self.addCalendar = function() {
    self.editCalendarModel.init({
      source: this,
      commandName: "add",
      command: function(id, name, organization) {
        ajax
          .command("create-calendar", {name: name, organization: organization})
          .success(function() {
            self.load();
            LUPAPISTE.ModalDialog.close();
          })
          .call();

        LUPAPISTE.ModalDialog.close();
      }
    });
    self.openCalendarEditDialog();
  };

  self.rmCalendar = function(indexFn) {
    // DUMMY DATA EDIT
    var index = indexFn();
    LUPAPISTE.ModalDialog.showDynamicYesNo(
            loc("areyousure"),
            loc("auth-admin.resource.calendar.delete.confirm"),
              {title: loc("yes"), fn: function() {
                self.items.splice(index, 1);
              }}
            );
    return false;
  };

  self.openCalendarEditDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-calendar");
  };

  self.init = function(data) {
    var calendars = data.calendars;
    self.items(calendars || []);
  };

  self.load = function() {
    ajax.query("list-calendars")
      .success(function(d) {
        self.init(d);
      })
      .call();
  };
};