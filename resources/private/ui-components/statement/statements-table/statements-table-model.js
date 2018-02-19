LUPAPISTE.StatementsTableModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.statements = params.statements;
  self.authorization = params.authModel;
  self.localisationKeys = params.localisationKeys;

  var user = lupapisteApp.models.currentUser;

  self.statementIdsWithAttachments = ko.pureComputed(function() {
    var statementIdsWithAttachments = [];
    _.forEach(lupapisteApp.services.attachmentsService.attachments(),
              function(attachment) {
                var targetType = util.getIn(attachment, ["target", "type"]);
                if (targetType === "statement") {
                  statementIdsWithAttachments.push(util.getIn(attachment, ["target", "id"]));
                }
              });
    return _.uniq(statementIdsWithAttachments);
  });

  self.hasAttachment = function(statement) {
    return _.includes(self.statementIdsWithAttachments(), statement.id());
  };

  self.isGiven = function(statement) {
    return _.includes(["given", "replyable", "replied"], util.getIn(statement, ["state"]));
  };

  self.getExternalText = function(statement) {
    var externalInfo = statement.external;
    if ( externalInfo && externalInfo.acknowledged && externalInfo.externalId ) {
      var partnerText = loc("application.statement.external-partner.in." + externalInfo.partner());
      var acknowledgedDateStr = moment(externalInfo.acknowledged()).format("D.M.YYYY");
      var fullText = loc("application.statement.external.received",
                         partnerText,
                         acknowledgedDateStr,
                         externalInfo.externalId());
      return fullText;
    } else {
      return "";
    }
  };

  self.isExternal = function(statement) {
    return _.isObject(statement.external);
  };

  var isStatementOwner = function(statement) {
    return util.getIn(statement, ["person", "userId"]) === user.id();
  };

  self.isRemovable = function(statement) {
    return !self.isExternal(statement) && user.isAuthority()  && !self.isGiven(statement);
  };

  self.canAccessStatement = function(statement) {
    return self.isGiven(statement) || user.isAuthority() || isStatementOwner(statement);
  };

  self.isStatementOverDue = function(statement) {
    return (statement.dueDate && !self.isGiven(statement) ) ? moment().isAfter(statement.dueDate(), "day") : false;
  };

  self.repliesEnabled = ko.pureComputed(function() {
    return self.authorization.ok("statement-replies-enabled");
  });

  self.isReplyable = function(statement) {
    return _.includes(["replyable"], util.getIn(statement, ["state"]));
  };

  self.showReplyState = function(statement) {
    var user = lupapisteApp.models.currentUser;
    if (self.application.userHasRole(user, "writer")) {
      return _.includes(["replied"], util.getIn(statement, ["state"]));
    } else {
      return _.includes(["replyable", "replied"], util.getIn(statement, ["state"]));
    }
  };

  var deleteStatementFromServer = function(statementId) {
    ajax
      .command("delete-statement", {id: self.application.id(), statementId: statementId})
      .success(function() {
        repository.load(self.application.id());
        return false;
      })
      .call();
    return false;
  };

  self.openStatement = function(tab, model) {
    pageutil.openPage("statement", [self.application.id(), model.id(), tab].join("/"));
    return false;
  };

  self.openDeleteDialog = function(model) {
    hub.send("show-dialog", {ltitle: "statement.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "statement.delete.message",
                                               yesFn: _.partial(deleteStatementFromServer, model.id())}});
  };

};
