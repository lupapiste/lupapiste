var users = (function() {
  "use strict";

  function toLoc(data, type, row) { return loc(data); }
  function toActive(data, type, row) { return loc(["users.data.enabled", data]); }
  function toOrgs(data, type, row) { return data ? data.join(", ") : ""; }
    

  function UsersModel(component, opts) {
    var self = this;
    
    self.component = component;
    self.table$ = $("table", component);
    
    self.availableRoles = _([null, "admin", "authority", "authorityAdmin", "applicant", "dummy"])
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

    self.ops = function(user) {
      return _.filter(opts.ops || [], function(op) { return op.showFor(user); });
    };
  
    self.toOps = function(td, sData, oData, iRow, iCol) {
      var user = oData.user,
          ops = _.filter(opts.ops || [], function(op) { return op.showFor(user); }),
          td = $(td);
      _.each(ops, function(op) {
        td.append($("<a>")
          .attr("href", "#")
          .attr("data-op", op.name)
          .text("[" + loc("users.op." + op.name) + "]"));
      });
    };
    
    self.userToRow = function(user) {
      return {user: user,
              0: user.email,
              1: user.firstName + " " + user.lastName,
              2: user.role,
              3: user.organizations ? user.organizations : [],
              4: user.enabled,
              5: ""}; // col 5 will be set by toOps 
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
        .command("users-for-datatables")
        .json({params: params})
        .success(_.compose(callback, self.processResults))
        .call();
    };

    self.rowCreated = function(row, data) {
      $(row).attr("data-user-email", data[0]);
    };
    
    self.redrawCallback = function(redraw) { if (redraw) self.redraw(); };
    
    self.table$.click(function(e) {
      var target = $(e.target),
          opName = target.attr("data-op"),
          op = _.find(opts.ops, function(op) { return op.name === opName; }),
          email = target.parent().parent().attr("data-user-email");
      if (!op || !email) return false;
      LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc("users.op." + op.name + ".title"),
          loc("users.op." + op.name + ".message"),
          {title: loc("yes"), fn: function() { op.operation(email, self.redrawCallback); }},
          {title: loc("cancel")});
      return false;
    });

    self.dataTable = self.table$.dataTable({
      bProcessing:      true, // don't hide this, it brakes layout.
      bServerSide:      true,
      sAjaxSource:      "",
      aoColumnDefs:     [{aTargets: [1,2,3,4,5], bSortable: false},
                         {aTargets: [2], mRender: toLoc},
                         {aTargets: [3], mRender: toOrgs},
                         {aTargets: [4], mRender: toActive},
                         {aTargets: [5], fnCreatedCell: self.toOps}],
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
      fnCreatedRow: self.rowCreated
    });

    self.redraw = function() { self.dataTable.fnDraw(true); };
    
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

})();
