LUPAPISTE.StatementEditModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.tab = "statement";

  self.authModel = params.authModel;

  var applicationId = params.applicationId;

  self.applicationTitle = params.applicationTitle;
  self.data = params.data;
  self.target = params.target;

  self.selectedStatus = ko.observable();
  self.text = ko.observable();
  self.inAttachment = ko.observable();

  self.showDueDate = self.disposedPureComputed( function() {

    return self.data && (self.authModel.ok( "save-statement-due-date")
                         || util.getIn( self.data, ["dueDate"]));
  });

  self.dueDateText = function( isLabel ) {
    var dueDate = util.getIn( self.data, ["dueDate"]);
    var text = loc( "add-statement-giver-maaraaika" );
    var fmt = isLabel ? "%s (%s)" : "%s: %s";
    return dueDate
      ? sprintf( fmt, text, util.formatMoment( util.toMoment( dueDate )))
      : text;
  };

  self.isAfterToday = function( m ) {
    var today = moment().startOf( "day" );
    return m.isAfter( today );
  };

  self.dueDateFn = function( obj ) {
    var m = obj.moment;
    if( obj.isValid && params.data && m ) {
      ajax.command("save-statement-due-date",
                     {id: applicationId(),
                      statementId: params.data().id(),
                      dueDate: m.valueOf(),
                      lang: loc.getCurrentLanguage()
                     })
          .success ( function() {
            hub.send("indicator", {style: "positive", message: "email.notification-sent"});
            repository.load(applicationId);
          })
        .error(util.showSavedIndicator)
        .call();
    } else {
      if( m && !self.isAfterToday( m )) {
        hub.send("indicator", {style: "negative",
                               message: "email.due-date-cant-be-in-the-past",
                               sticky: false});
      }
    }
  };

  self.disposedSubscribe( self.data, function( data ) {
    self.selectedStatus(util.getIn(data, ["status"]));
    self.text(util.getIn(data, ["text"]));
    self.inAttachment(util.getIn(data, ["in-attachment"]));
  });

  var commands = params.commands;

  self.statuses = ko.observableArray([]);

  function dataView( target ) {
    return ko.mapping.toJS( _.pick( ko.unwrap( target ), ["id", "type"]));
  }

  self.attachments = self.disposedPureComputed( function() {
    return _.filter(lupapisteApp.services.attachmentsService.attachments(),
      function(attachment) {
        return _.isEqual(dataView(attachment().target), dataView(self.target));
      });
  });

  function attachmentsContainsStatement() {
    return _.some(self.attachments(), function(a) {
      var type = util.getIn(a, ["type"]);
      return _.isEqual(type, {"type-group": "ennakkoluvat_ja_lausunnot", "type-id": "lausunto"});
    });
  }

  var submitAllowed = ko.pureComputed(function() {
    var inAttachmentOk = self.inAttachment() && self.attachments().length > 0 && attachmentsContainsStatement();
    return !!self.selectedStatus() && (inAttachmentOk || (!self.inAttachment() && !!self.text() && !_.isEmpty(self.text())));
  });

  self.showAttachmentGuide = self.disposedPureComputed(function() {
    return self.inAttachment() && (self.attachments().length === 0 || !attachmentsContainsStatement());
  });

  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(commands.submit);
  });

  self.isDraft = ko.pureComputed(function() {
    return _.includes(["requested", "draft"], util.getIn(self.data, ["state"]));
  });

  self.showStatement = ko.pureComputed(function() {
    return self.isDraft() ? self.enabled() : true;
  });

  self.coverNote = ko.pureComputed(function() {
    var canViewCoverNote = util.getIn(self.data, ["person", "userId"]) === lupapisteApp.models.currentUser.id() || lupapisteApp.models.currentUser.isAuthority();
    return self.tab === "statement" && canViewCoverNote ? util.getIn(self.data, ["saateText"]) : "";
  });

  self.disposedSubscribe( self.text, function(value) {
    if(util.getIn(self.data, ["text"]) !== value) {
      hub.send("statement::changed", {tab: self.tab, path: ["text"], value: value });
    }
  });

  self.disposedSubscribe( self.selectedStatus, function(value) {
    if(util.getIn(self.data, ["status"]) !== value) {
      hub.send("statement::changed", {tab: self.tab, path: ["status"], value: value});
    }
  });

  self.disposedSubscribe(self.inAttachment, function(value) {
    if(util.getIn(self.data, ["in-attachment"]) !== value) {
      hub.send("statement::changed", {tab: self.tab, path: ["in-attachment"], value: value});
    }
  });

  hub.send("statement::submitAllowed", {tab: self.tab, value: submitAllowed()});

  self.disposedSubscribe(submitAllowed, function(value) {
    hub.send("statement::submitAllowed", {tab: self.tab, value: value});
  });

  function initStatementStatuses(appId) {
    ajax
    .query("get-possible-statement-statuses", {id: appId})
    .success(function(resp) {
      var sorted = _(resp.data)
        .map(function(item) { return {id: item, name: loc(["statement", item])}; })
        .sortBy("name")
        .value();
      self.statuses(sorted);
    })
    .call();
  }

  if (applicationId()) {
    initStatementStatuses(applicationId());
  }
  self.disposedSubscribe( applicationId, function(appId) {
    if (appId) {
      initStatementStatuses(appId);
    }
  });
};
