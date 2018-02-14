var users = (function($) {
  "use strict";

  function toActive(data) { return loc(["users.data.enabled", data]); }
  function rowCreated(row, data) { $(row).attr("data-user-email", data[0]); }

  function toLocalizedOrgAuthz(data) {
    if (_.isObject(data)) {
      return _.map(data, function(roles, org) {
        var localizedRoles = _.map(roles, function(role) {
          return loc(["authorityrole", role]);
        });
        return "<b>" + org + ":</b> " + localizedRoles.join(", ");
      }).join(", ");
    } else {
      return loc(data);
    }
  }

  function UsersModel(component, opts) {
    var self = this;

    self.component = component;
    self.table$ = $("table", component);

    self.availableRoles = _([null, "admin", "authority", "authorityAdmin", "applicant", "dummy", "financialAuthority"])
      .map(function(id) { return {id: id, name: loc(id ? id : "users.filters.role.all")}; })
      .value();

    self.availableEnableds = _([null, true, false])
      .map(function(id) { return {id: id, name: loc(id === null ? "users.filters.enabled.all" : "users.data.enabled." + id)}; })
      .value();

    self.filters = {
      role:     ko.observable(),
      enabled:  ko.observable(),
      search:   ko.observable()
    };

    self.show = {
      filters:        !opts.hideFilters,
      roleFilter:     !opts.hideRoleFilter,
      enabledFilter:  !opts.hideEnabledFilter,
      search:         !opts.hideSearch
    };

    self.toOps = function(td, sData, oData) {
      var user = oData.user,
          td$ = $(td);
      td$.attr("class", "users-table-actions");
      _.each(opts.ops, function(op) {
        if (op.showFor(user)) {
          td$.append($("<button>")
            .attr("class", op.button)
            .attr("data-op", op.name)
              .append($("<i>")
                  .attr("class", op.icon))
              .append($("<span>")
                  .text(loc(["users.op", op.name]))));
        }
      });
    };

    self.userToRow = function(user) {
      return {user: user,
              0: user.email,
              1: user.lastName + " " + user.firstName,
              2: user.orgAuthz ? user.orgAuthz : user.role,
              3: user.enabled,
              4: ""}; // column 4 will be set by toOps
    };

    self.processResults = function(r) {
      var data = r.data;
      return {aaData:               _.map(data.rows, self.userToRow),
              iTotalRecords:        data.total,
              iTotalDisplayRecords: data.display,
              sEcho:                data.echo};
    };

    self.fetch = function(source, data, callback) {
      var params = _(data)
        .concat(_.map(self.filters, function(v, k) { return {name: "filter-" + k, value: v()}; }))
        .reduce(function(m, p) { m[p.name] = p.value; return m; }, {});
      ajax
        .datatables("users-for-datatables", {params: params})
        .success(_.flowRight(callback, self.processResults))
        .call();
    };

    self.redraw = function() { self.dataTable.fnDraw(true); };
    self.redrawCallback = function(results) {
      if (results) {
        self.redraw();
      }

      var resultText = "";
      if (_.isObject(results)) {
        _.forOwn(results, function(value, key) {
          if (key !== "ok") {
            resultText += key + " = " + value;
          }
        });
      }
      if (resultText !== "") {
        resultText = "<textarea rows='10' style='width:100%' readonly='readonly'>" + resultText + "</textarea>";
        var title = results.ok ? "Toiminnon tulokset" : loc("error.unknown");
        LUPAPISTE.ModalDialog.showDynamicOk(title, resultText, undefined, {html: true});
      }
    };

    self.table$.click(function(e) {
      var target = $(e.target),
          opName = target.attr("data-op"),
          op = _.find(opts.ops, function(op) { return op.name === opName; }),
          email = target.parent().parent().attr("data-user-email");
      if (!op || !email) {
        return false;
      }
      if (op.rowOperationFn) {
        op.rowOperationFn(self.dataTable.fnGetData(target.closest("tr")[0]));
      } else {
        LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc(["users.op", op.name, "title"]),
          loc(["users.op", op.name, "message"], email),
          {title: loc("yes"), fn: function() { op.operation(email, self.redrawCallback); }},
          {title: loc("cancel")});
      }
      return false;
    });

    self.dataTable = self.table$.dataTable({
      bProcessing:      true, // don't hide this, it brakes layout.
      bServerSide:      true,
      sAjaxSource:      "",
      aoColumnDefs:     [{aTargets: [1,2,3,4], bSortable: false},
                         {aTargets: [2], mRender: toLocalizedOrgAuthz},
                         {aTargets: [3], mRender: toActive},
                         {aTargets: [4], fnCreatedCell: self.toOps}],
      aaSorting:        [[0, "desc"]],
      sDom:             "<t><<r><p><i><l>>", // <'table-filter' f>
      iDisplayLength:   10,
      oLanguage:        {sLengthMenu:   loc("users.table.lengthMenu"),
                         sProcessing:   "&nbsp;",
                         sSearch:       loc("search") + ":",
                         sZeroRecords:  loc("users.table.zeroRecords"),
                         oPaginate:     {"sNext": loc("next"), "sPrevious": loc("previous")},
                         sInfo:         loc("users.table.info"),
                         sInfoFiltered: ""},
      fnServerData: self.fetch,
      fnCreatedRow: rowCreated
    });

    _.each(self.filters, function(o) { o.subscribe(_.throttle(self.redraw, 600)); });

    component.applyBindings(self);
    return self;
  }

  return {
    create: function(targetOrId, opts) {
      var component = $("#users-templates .users-table").clone();
      var usersModel = new UsersModel(component, opts);
      (_.isString(targetOrId) ? $("#" + targetOrId) : targetOrId).append(component);
      return usersModel;
    }
  };

})(jQuery);
