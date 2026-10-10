#!/usr/bin/env node

/**
 * EventEase - Repeatable Demo Data Seeder
 * 
 * Safely provisions rich demonstration data for college evaluation and demonstration:
 *   - Demo Accounts: Admin, Organizer, Student Attendee, Volunteer
 *   - Rich Events: Conferences, Workshops, Hackathons, and Completed Seminars
 *   - Registrations, Teams, Attendances, and Issued Certificates
 *   - RAG Knowledge Documents for AI Assistant query testing
 * 
 * SAFEGUARDS:
 *   - By default, checks if demo data already exists (detected via admin@eventease.io).
 *     If present, aborts safely without mutating or duplicating any records.
 *   - Does NOT delete unrelated user records or drop/truncate application tables.
 *   - Run with '--reset' or '--force' to clear and re-populate only demo records.
 * 
 * Usage:
 *   node scripts/seed_demo_data.js
 *   node scripts/seed_demo_data.js --reset
 */

import fs from 'fs'
import path from 'path'
import os from 'os'
import { execSync } from 'child_process'

const BASE_URL = process.env.BASE_URL || 'http://localhost:3000'
const DEMO_PASSWORD = process.env.DEMO_PASSWORD || 'Password123!'

// Demo Accounts Definitions
const ACCOUNTS = {
  admin: {
    name: 'Dr. Evelyn Vance (Platform Administrator)',
    email: 'admin@eventease.io',
    password: DEMO_PASSWORD,
    phone: '555-0100',
    role: 'ADMIN',
  },
  organizer: {
    name: 'Sarah Jenkins (Faculty Coordinator)',
    email: 'organizer@eventease.io',
    password: DEMO_PASSWORD,
    phone: '555-0101',
    role: 'ORGANIZER',
  },
  student: {
    name: 'Alex Rivera (Computer Science Student)',
    email: 'student@eventease.io',
    password: DEMO_PASSWORD,
    phone: '555-0102',
    role: 'USER',
  },
  peer: {
    name: 'Maya Patel (Engineering Student)',
    email: 'maya@eventease.io',
    password: DEMO_PASSWORD,
    phone: '555-0103',
    role: 'USER',
  },
  volunteer: {
    name: 'David Kim (Student Volunteer)',
    email: 'volunteer@eventease.io',
    password: DEMO_PASSWORD,
    phone: '555-0104',
    role: 'USER',
  },
}

import { fileURLToPath } from 'url'

function getDbPassword() {
  try {
    const scriptDir = path.dirname(fileURLToPath(import.meta.url))
    const candidates = [
      path.resolve(process.cwd(), '.env'),
      path.resolve(process.cwd(), '../.env'),
      path.resolve(scriptDir, '../.env'),
    ]
    for (const envPath of candidates) {
      if (fs.existsSync(envPath)) {
        const content = fs.readFileSync(envPath, 'utf-8')
        const match = content.match(/DB_ROOT_PASSWORD=(.*)/)
        if (match && match[1]) {
          return match[1].trim()
        }
      }
    }
  } catch {
    // fallback
  }
  return 'password'
}

const DEMO_EVENT_TITLES = [
  'Global AI & Cloud Computing Summit 2026',
  'Hands-on Full-Stack Microservices Bootcamp',
  'Inter-Collegiate Hackathon 2026',
  'Distinguished Guest Seminar: Distributed Systems Architecture',
]

function getDockerBin() {
  const possiblePaths = [
    '/Users/jeevanayerra/.docker/bin/docker',
    path.join(os.homedir(), '.docker/bin/docker'),
    '/usr/local/bin/docker',
    '/opt/homebrew/bin/docker',
  ]
  for (const p of possiblePaths) {
    try {
      if (fs.existsSync(p)) return p
    } catch {}
  }
  return 'docker'
}

function execSql(sql) {
  const rootPw = getDbPassword()
  const dockerBin = getDockerBin()
  const dockerCmd = `"${dockerBin}" exec eventease-db mysql -u root -p"${rootPw}" -e "USE eventease; ${sql}"`
  try {
    return execSync(dockerCmd, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'pipe'] })
  } catch (err) {
    console.error('SQL Execution Error:', err.message)
    return null
  }
}

async function api(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  })
  const text = await res.text()
  let data = null
  try {
    data = JSON.parse(text)
  } catch {
    data = text
  }
  return { status: res.status, ok: res.ok, data }
}

async function checkExistingDemoData() {
  const rootPw = getDbPassword()
  const dockerBin = getDockerBin()
  const checkCmd = `"${dockerBin}" exec eventease-db mysql -u root -p"${rootPw}" -N -s -e "USE eventease; SELECT COUNT(*) FROM users WHERE email = '${ACCOUNTS.admin.email}';"`
  try {
    const out = execSync(checkCmd, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
    return parseInt(out, 10) > 0
  } catch {
    return false
  }
}

function cleanDemoData() {
  console.log('🧹 Cleaning existing demo records with strict ownership boundaries...')
  const demoEmails = Object.values(ACCOUNTS).map((a) => `'${a.email}'`).join(',')
  const demoTitles = DEMO_EVENT_TITLES.map((t) => `'${t.replace(/'/g, "\\'")}'`).join(',')

  // Strict ownership cleanup: deletes only records demonstrably owned by demo users or demo events.
  // Foreign keys are resolved in strict dependency order without risking unrelated tables or rows.
  const cleanupSql = `
    DELETE c FROM certificates c 
      INNER JOIN registrations r ON c.registration_id = r.id 
      WHERE r.user_id IN (SELECT id FROM users WHERE email IN (${demoEmails})) 
         OR r.event_id IN (SELECT id FROM events WHERE title IN (${demoTitles}));

    DELETE a FROM attendances a 
      INNER JOIN registrations r ON a.registration_id = r.id 
      WHERE r.user_id IN (SELECT id FROM users WHERE email IN (${demoEmails})) 
         OR r.event_id IN (SELECT id FROM events WHERE title IN (${demoTitles}));

    DELETE tm FROM team_members tm 
      WHERE tm.user_id IN (SELECT id FROM users WHERE email IN (${demoEmails})) 
         OR tm.team_id IN (SELECT id FROM teams WHERE event_id IN (SELECT id FROM events WHERE title IN (${demoTitles})));

    DELETE t FROM teams t 
      WHERE t.event_id IN (SELECT id FROM events WHERE title IN (${demoTitles}));

    DELETE r FROM registrations r 
      WHERE r.user_id IN (SELECT id FROM users WHERE email IN (${demoEmails})) 
         OR r.event_id IN (SELECT id FROM events WHERE title IN (${demoTitles}));

    DELETE vr FROM volunteer_requirements vr 
      WHERE vr.event_id IN (SELECT id FROM events WHERE title IN (${demoTitles}));

    DELETE ed FROM event_documents ed 
      WHERE ed.event_id IN (SELECT id FROM events WHERE title IN (${demoTitles}));

    DELETE e FROM events e 
      WHERE e.title IN (${demoTitles}) 
        AND e.organizer_id IN (SELECT id FROM users WHERE email = '${ACCOUNTS.organizer.email}');

    DELETE FROM users 
      WHERE email IN (${demoEmails});
  `
  execSql(cleanupSql)
}

async function main() {
  const args = process.argv.slice(2)
  const isReset = args.includes('--reset') || args.includes('--force')

  console.log('==================================================================')
  console.log('  EventEase - Comprehensive Demonstration Data Seeder')
  console.log('==================================================================')

  const exists = await checkExistingDemoData()
  if (exists) {
    if (!isReset) {
      console.log('ℹ️  Demo data is already initialized in the database.')
      console.log('   (admin@eventease.io detected). No changes applied.')
      console.log('\n   To reset and re-populate demo data, execute:')
      console.log('     node scripts/seed_demo_data.js --reset\n')
      printCredentials()
      return
    } else {
      cleanDemoData()
    }
  }

  console.log('\n🚀 Step 1: Provisioning Demo User Accounts...')
  const tokens = {}
  const userIds = {}

  for (const [key, acc] of Object.entries(ACCOUNTS)) {
    // Signup account
    const signupRes = await api('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({
        name: acc.name,
        email: acc.email,
        password: acc.password,
        phone: acc.phone,
      }),
    })
    if (!signupRes.ok && signupRes.status !== 409) {
      console.error(`Failed to register ${acc.email}:`, signupRes.data)
      process.exit(1)
    }

    // Login to obtain ID
    const loginRes = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: acc.email, password: acc.password }),
    })
    userIds[key] = loginRes.data.userId

    // Promote Role if elevated
    if (acc.role !== 'USER') {
      execSql(`UPDATE users SET role = '${acc.role}' WHERE id = ${userIds[key]};`)
    }

    // Refresh token with updated role
    const freshLogin = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: acc.email, password: acc.password }),
    })
    tokens[key] = freshLogin.data.token
    console.log(`   ✓ ${acc.role.padEnd(9)}: ${acc.email} (ID: ${userIds[key]})`)
  }

  console.log('\n🚀 Step 2: Creating Demonstration Events...')

  // Event 1: Conference
  const ev1Res = await api('/api/events', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      title: 'Global AI & Cloud Computing Summit 2026',
      description: 'An international conference bringing together leading researchers, industry engineers, and student builders to explore large language models, agentic computing, and distributed cloud systems.',
      category: 'CONFERENCE',
      date: '2026-11-20',
      startTime: '09:00:00',
      endTime: '17:30:00',
      venue: 'University Grand Auditorium & Stream Hall A',
      capacity: 300,
      eligibility: 'Open to all enrolled university students, faculty, and industry professionals.',
      registrationDeadline: '2026-11-15T23:59:59',
      organizerId: userIds.organizer,
    }),
  })
  const ev1Id = ev1Res.data.id

  // Approve & Publish Event 1
  await api(`/api/events/${ev1Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.admin}` },
    body: JSON.stringify({ status: 'APPROVED' }),
  })
  await api(`/api/events/${ev1Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ status: 'PUBLISHED' }),
  })
  console.log(`   ✓ [PUBLISHED] Event #1: Global AI & Cloud Computing Summit 2026`)

  // Event 1 Volunteer Requirement
  await api('/api/volunteer-requirements', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      eventId: ev1Id,
      requiredCount: 8,
      description: 'Assist with morning attendee badge check-in, microphone management, and speaker lounge coordination.',
    }),
  })

  // Event 2: Hands-on Workshop
  const ev2Res = await api('/api/events', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      title: 'Hands-on Full-Stack Microservices Bootcamp',
      description: 'Practical 6-hour intensive coding bootcamp on containerized Spring Boot 3 microservices and modern React 19 SPAs with JWT authentication and Docker Compose deployment.',
      category: 'WORKSHOP',
      date: '2026-11-25',
      startTime: '10:00:00',
      endTime: '16:00:00',
      venue: 'Computer Science Complex - Lab 402',
      capacity: 50,
      eligibility: 'Basic knowledge of Java or JavaScript. Bring your laptop with Docker installed.',
      registrationDeadline: '2026-11-22T23:59:59',
      organizerId: userIds.organizer,
    }),
  })
  const ev2Id = ev2Res.data.id

  await api(`/api/events/${ev2Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.admin}` },
    body: JSON.stringify({ status: 'APPROVED' }),
  })
  await api(`/api/events/${ev2Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ status: 'PUBLISHED' }),
  })
  console.log(`   ✓ [PUBLISHED] Event #2: Hands-on Full-Stack Microservices Bootcamp`)

  // Event 3: Hackathon with Teams
  const ev3Res = await api('/api/events', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      title: 'Inter-Collegiate Hackathon 2026',
      description: 'Annual 36-hour hackathon challenging teams to develop innovative software solutions across Healthcare, Sustainable Energy, and Education technology.',
      category: 'HACKATHON',
      date: '2026-12-05',
      startTime: '08:00:00',
      endTime: '20:00:00',
      venue: 'Innovation & Entrepreneurship Hub - Arena B',
      capacity: 120,
      eligibility: 'Teams of 2 to 4 undergraduate or graduate students.',
      registrationDeadline: '2026-11-30T23:59:59',
      organizerId: userIds.organizer,
    }),
  })
  const ev3Id = ev3Res.data.id

  await api(`/api/events/${ev3Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.admin}` },
    body: JSON.stringify({ status: 'APPROVED' }),
  })
  await api(`/api/events/${ev3Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ status: 'PUBLISHED' }),
  })
  console.log(`   ✓ [PUBLISHED] Event #3: Inter-Collegiate Hackathon 2026`)

  // Event 4: Completed Event with Attendance and Certificate
  const ev4Res = await api('/api/events', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      title: 'Distinguished Guest Seminar: Distributed Systems Architecture',
      description: 'Keynote lecture on consensus algorithms, Raft, Paxos, and multi-region replication in modern cloud databases.',
      category: 'SEMINAR',
      date: '2026-10-01',
      startTime: '14:00:00',
      endTime: '16:00:00',
      venue: 'Hall of Champions',
      capacity: 200,
      eligibility: 'Open to the campus community.',
      registrationDeadline: '2026-09-30T23:59:59',
      organizerId: userIds.organizer,
    }),
  })
  const ev4Id = ev4Res.data.id

  await api(`/api/events/${ev4Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.admin}` },
    body: JSON.stringify({ status: 'APPROVED' }),
  })
  await api(`/api/events/${ev4Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ status: 'PUBLISHED' }),
  })

  console.log('\n🚀 Step 3: Registering Participants, Teams, and Issuing Certificates...')

  // Student registers for Event 1 (Conference)
  await api('/api/registrations', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.student}` },
    body: JSON.stringify({ eventId: ev1Id, userId: userIds.student, type: 'PARTICIPANT' }),
  })

  // Volunteer registers for Event 1 & is Accepted
  const volReg = await api('/api/registrations', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.volunteer}` },
    body: JSON.stringify({ eventId: ev1Id, userId: userIds.volunteer, type: 'VOLUNTEER' }),
  })
  await api(`/api/registrations/${volReg.data.id}/volunteer-status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ status: 'ACCEPTED' }),
  })
  console.log(`   ✓ Volunteer registered & accepted for Event #1`)

  // Student & Peer register for Event 3 (Hackathon)
  await api('/api/registrations', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.student}` },
    body: JSON.stringify({ eventId: ev3Id, userId: userIds.student, type: 'PARTICIPANT' }),
  })
  await api('/api/registrations', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.peer}` },
    body: JSON.stringify({ eventId: ev3Id, userId: userIds.peer, type: 'PARTICIPANT' }),
  })

  // Student creates Team and adds Peer
  const teamRes = await api('/api/teams', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.student}` },
    body: JSON.stringify({
      name: 'ByteCrafters',
      eventId: ev3Id,
      leaderId: userIds.student,
    }),
  })
  await api('/api/team-members', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.student}` },
    body: JSON.stringify({
      teamId: teamRes.data.id,
      userId: userIds.peer,
    }),
  })
  console.log(`   ✓ Team 'ByteCrafters' created with leader and member for Hackathon`)

  // Student registers for Event 4, attendance is marked, event completed, cert issued
  const ev4Reg = await api('/api/registrations', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.student}` },
    body: JSON.stringify({ eventId: ev4Id, userId: userIds.student, type: 'PARTICIPANT' }),
  })
  await api('/api/attendance', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ registrationId: ev4Reg.data.id, attended: true }),
  })
  await api(`/api/events/${ev4Id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({ status: 'COMPLETED' }),
  })
  await api('/api/certificates', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      registrationId: ev4Reg.data.id,
      certificateUrl: 'https://eventease.io/certificates/dist-sys-2026.pdf',
    }),
  })
  console.log(`   ✓ [COMPLETED] Event #4: Attendance marked & Certificate issued`)

  console.log('\n🚀 Step 4: Seeding Knowledge Documents for AI Assistant RAG...')

  // Documents for Event 1
  await api('/api/event-documents', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      eventId: ev1Id,
      title: 'Global AI Summit 2026 - Comprehensive Agenda & Keynotes',
      type: 'FAQ',
      content: 'The summit opens at 09:00 AM in the Grand Auditorium with keynote speaker Dr. Aris Thorne discussing Foundation Models. Keynote sessions run until 11:30 AM, followed by track presentations on Cloud Kubernetes clusters and ONNX Runtime performance. Lunch is served from 12:30 to 13:45 PM in Dining Hall West. Free high-speed Wi-Fi network is "EventEase-Guest" with access password "Summit2026". All attendees must wear their visitor badge at all times.',
    }),
  })

  await api('/api/event-documents', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      eventId: ev1Id,
      title: 'Global AI Summit 2026 - Venue Logistics and Parking Guide',
      type: 'GUIDELINES',
      content: 'Parking is available at Campus Parking Garage North on Levels 2 and 3 for free with event registration confirmation. Shuttle buses run every 15 minutes between the Metro Transit Center and the Grand Auditorium entrance. Accessible seating and live captioning are provided in Row F of the main auditorium.',
    }),
  })

  // Documents for Event 2
  await api('/api/event-documents', {
    method: 'POST',
    headers: { Authorization: `Bearer ${tokens.organizer}` },
    body: JSON.stringify({
      eventId: ev2Id,
      title: 'Full-Stack Bootcamp - Software Prerequisites and Git Setup',
      type: 'RULES',
      content: 'Participants must have a personal laptop running Windows, macOS, or Linux with minimum 8GB RAM. Ensure Docker Desktop 4.28+ is installed and running. Node.js 20 LTS and Java 21 JDK are required. Workshop repositories will be cloned from github.com/eventease/bootcamp-starter. Cloud sandbox API keys for database hosting will be provided during session orientation.',
    }),
  })

  console.log(`   ✓ 3 Knowledge documents indexed for semantic RAG search`)

  console.log('\n==================================================================')
  console.log('✅ Demonstration Data Seeding Successfully Completed!')
  console.log('==================================================================')
  printCredentials()
}

function printCredentials() {
  console.log('\n🔑 Demonstration User Accounts:')
  console.log('------------------------------------------------------------------')
  console.log('1. Administrator Portal:')
  console.log(`   Email:    admin@eventease.io`)
  console.log(`   Password: ${DEMO_PASSWORD}`)
  console.log(`   URL:      ${BASE_URL}/admin/dashboard`)
  console.log('   Role:     Review pending events, manage users, platform governance')
  console.log('')
  console.log('2. Organizer Workspace:')
  console.log(`   Email:    organizer@eventease.io`)
  console.log(`   Password: ${DEMO_PASSWORD}`)
  console.log(`   URL:      ${BASE_URL}/organizer/dashboard`)
  console.log('   Role:     Create events, manage rosters, approve volunteers, issue certs')
  console.log('')
  console.log('3. Student Attendee:')
  console.log(`   Email:    student@eventease.io`)
  console.log(`   Password: ${DEMO_PASSWORD}`)
  console.log(`   URL:      ${BASE_URL}/events`)
  console.log('   Role:     Browse events, register tickets, lead teams, view certificates')
  console.log('------------------------------------------------------------------')
  console.log('✨ Live Application Links:')
  console.log(`   Frontend Web App:   ${BASE_URL}/`)
  console.log(`   Swagger UI Docs:    ${BASE_URL}/swagger-ui/index.html`)
  console.log(`   OpenAPI 3.1 JSON:   ${BASE_URL}/v3/api-docs`)
  console.log(`   Actuator Health:    ${BASE_URL}/actuator/health`)
  console.log(`   AI Assistant Chat:  ${BASE_URL}/assistant`)
  console.log('==================================================================\n')
}

main().catch((err) => {
  console.error('Fatal error during seeding:', err)
  process.exit(1)
})
