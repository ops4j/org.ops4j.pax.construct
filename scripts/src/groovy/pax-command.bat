@echo off
SETLOCAL
set _SCRIPTS_=%~dp0
call "%_SCRIPTS_%\\pax-validate"

if ""=="%PAX_CONSTRUCT_VERSION%" set PAX_CONSTRUCT_VERSION=${version}
set PAX_PLUGIN=org.ops4j.pax.construct:maven-pax-plugin:%PAX_CONSTRUCT_VERSION%

set _BATFILE_=%0<%
  options.order.each {
%>
set _${options[it].name.toUpperCase()}_=<% } %>

goto getopts
:shift_2
shift
:shift_1
shift

:getopts<%
  options.order.each {
%>
if "%1"=="-${it}" set _${options[it].name.toUpperCase()}_=%2
if "%1"=="-${it}" goto shift_2<% } %>
if "%1"=="-h" goto help
if "%1"=="--" goto endopts
if "%1"=="" goto endopts

echo %_BATFILE_%: illegal option -- %1
:help
echo pax-${mojo}<%
  options.order.each {
    %> <%
    if( options[it].optional ) {
      %>[<%
    }
    %>-${it} ${options[it].name}<%
    if( options[it].optional ) {
      %>]<%
    }
  } %> [-- mvnOpts ...]
goto done
:endopts

shift
shift

set _EXTRA_=%PAX_CONSTRUCT_OPTIONS% %0 %1 %2 %3 %4 %5 %6 %7 %8 %9
<%
  allRequired = true
  allOptional = true
  options.order.each {
    if( options[it].optional )
      allRequired = false
    else
      allOptional = false
  }

  if( !allOptional ) {
    if( !allRequired ) {
      options.order.each {
        if( !options[it].optional ) {
%>
if ""=="%_${options[it].name.toUpperCase()}_%" goto request_input<% } } %>
goto skip_input

:request_input<%
    }
    options.order.each {
%>
if ""=="%_${options[it].name.toUpperCase()}_%" set /p _${options[it].name.toUpperCase()}_="${options[it].name} (${options[it].example}) ? "<%
    }
    if( !allRequired ) {
%>
:skip_input<%
    }
%>
<%
  }
  options.order.each {
%>
if ""=="%_${options[it].name.toUpperCase()}_%" set _${options[it].name.toUpperCase()}_=${options[it].example}<% } %>

@echo on
mvn %PAX_PLUGIN%:${mojo}<%
  options.order.each {
%> -D${options[it].name}=%_${options[it].name.toUpperCase()}_%<% } %> %_EXTRA_%
:done
